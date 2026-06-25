# How It Works

A top-down walkthrough of what happens inside KacheController on each operation, using MongoDB + Redis as the concrete example.

---

## The two Redis keys every collection gets

When any method is called on a `MongoCollection`, KacheController derives two Redis keys from the collection's namespace:

```
"myApp:users"          ← primary key   (Redis HASH)
"myApp:users:volatile" ← volatile key  (Redis HASH)
```

Both are **Redis hashes** — a map of `field → value` stored under a single key — not simple string keys.

**Primary hash** — one field per document. Field = document `id`, value = JSON-serialised document:

```
myApp:users
  "abc123"  →  '{"id":"abc123","firstName":"Alice","lastName":"Smith"}'
  "def456"  →  '{"id":"def456","firstName":"Bob","lastName":"Jones"}'
```

**Volatile hash** — one field per cached query result. Field = whatever key you named it, value = JSON array or scalar:

```
myApp:users:volatile
  "users:role:admin"  →  '[{"id":"def456",...}]'
  "users:count"       →  '42'
```

---

## `get` — read one document

```kotlin
controller.get(userId, usersCollection, User.serializer()) {
    find(Filters.eq("_id", userId)).firstOrNull()
}
```

```
HGET "myApp:users" "abc123"
  → hit:  deserialise JSON → return User (MongoDB never contacted)
  → miss: run lambda (MongoDB find)
          HSET "myApp:users" "abc123" "{...json...}"
          DEL  "myApp:users:volatile"
          return User
```

The volatile clear on a miss looks counterintuitive, but it's a safety measure: a cache miss means the primary hash was out of sync, so any derived query results (filters, counts) stored in the volatile hash may also be stale.

---

## `getAll` — read a collection (two modes)

### Default key — all documents

```kotlin
controller.getAll(usersCollection, User.serializer()) {
    find().toList()
}
```

```
EXISTS "myApp:users"
  → hit:  HGETALL "myApp:users"
          filter out __kache_empty__ sentinel
          deserialise each value → return List<User>

  → miss: run lambda (MongoDB find().toList())
          HSET "myApp:users" { "abc123": "{...}", "def456": "{...}", ... }
          return List<User>
```

Each document is its own field. This means a `set` for one user only updates one field, and a `remove` only deletes one field — the rest of the cached collection is untouched.

**The empty sentinel:** if MongoDB returns an empty list, writing nothing leaves the hash non-existent. The next `getAll` would see `EXISTS = false` and query MongoDB again forever. Instead, `HSET "myApp:users" "__kache_empty__" "1"` is written. Now `EXISTS` returns true, `HGETALL` returns the sentinel, it gets filtered out, and the caller gets `[]` without touching MongoDB.

### Custom key — filtered or paginated queries

```kotlin
controller.getAll(
    usersCollection, User.serializer(),
    cacheKey = "users:role:admin",
) {
    find(Filters.eq("role", "admin")).toList()
}
```

A custom `cacheKey` triggers a completely different code path — the result is stored **inside the volatile hash**, not as its own Redis key:

```
HGET "myApp:users:volatile" "users:role:admin"
  → hit:  deserialise JSON array → return List<User>
  → miss: run lambda (MongoDB filtered find)
          HSET "myApp:users:volatile" "users:role:admin" "[{...},{...}]"
          return List<User>
```

Because the result lives in the volatile hash, every write operation's `DEL "myApp:users:volatile"` invalidates it automatically. You never manually track which cached queries need clearing when a document changes.

---

## `set` — write one document

```kotlin
controller.set(usersCollection, User.serializer()) {
    findOneAndUpdate(filter, update, options)
}
```

```
run lambda (MongoDB findOneAndUpdate) → updatedUser
HSET "myApp:users" "abc123" "{...updated json...}"
DEL  "myApp:users:volatile"
return updatedUser
```

The lambda runs first. Its return value is what gets cached — so the cache always reflects exactly what the database confirmed was written, not what you intended to write.

`invalidateVolatiles = false` skips the `DEL` step for writes that cannot affect any cached query result (e.g. updating a `lastSeen` timestamp while your volatile results are role-count queries).

---

## `setAll` — write multiple documents

```kotlin
controller.setAll(usersCollection, User.serializer()) {
    if (insertMany(users).wasAcknowledged()) users else emptyList()
}
```

```
run lambda → List<User>
if list is empty:
    HSET "myApp:users" "__kache_empty__" "1"
else:
    HSET "myApp:users" { "abc123": "{...}", "def456": "{...}", ... }
DEL "myApp:users:volatile"
return true
```

---

## `getVolatile` — cache a computed value

For anything that isn't a simple document list — counts, aggregates, paginated slices:

```kotlin
controller.getVolatile("users:count", usersCollection, Long.serializer()) {
    countDocuments()
}
```

```
HGET "myApp:users:volatile" "users:count"
  → hit:  deserialise → return 42L
  → miss: run lambda (MongoDB countDocuments())
          HSET "myApp:users:volatile" "users:count" "42"
          return 42L
```

Same volatile hash, same automatic invalidation on any write to the collection.

---

## `remove` — delete one document

```kotlin
controller.remove(userId, usersCollection) {
    deleteOne(Filters.eq("_id", userId)).wasAcknowledged()
}
```

```
run lambda (MongoDB deleteOne)
  → false: nothing changes in Redis
  → true:
    HDEL "myApp:users" "abc123"    ← remove just this field
    DEL  "myApp:users:volatile"    ← clear all volatile results
    return true
```

Only the specific document field is evicted. All other cached documents in the collection remain.

---

## `removeAll` — delete many documents

```kotlin
controller.removeAll(usersCollection) {
    deleteMany(filter).deletedCount > 0
}
```

```
run lambda (MongoDB deleteMany)
  → false: nothing changes in Redis
  → true:
    DEL "myApp:users"              ← drop entire hash (atomic, one command)
    DEL "myApp:users:volatile"
    return true
```

`DEL` on the primary hash is one atomic command — no race window between evicting individual fields. The trade-off is that it evicts all cached documents for the collection, including ones that weren't deleted. The next `getAll` will be a cache miss and re-warms the collection from MongoDB.

---

## `cacheEnabled` — the bypass switch

Every internal method starts with:

```kotlin
if (!cacheEnabled()) return getData() // or setData() / deleteData()
```

`cacheEnabled` is a `() -> Boolean` evaluated fresh on every call. When it returns false, your lambda runs directly and Redis is never touched. Wire it to a feature flag, environment variable, or per-request context without rebuilding the controller.

---

## Full request flow (visual)

```
Your code           MongoKacheController      Redis              MongoDB
    │                       │                   │                   │
    │── get(id) ───────────▶│                   │                   │
    │                       │── HGET primary ──▶│                   │
    │                       │◀── hit: json ──────│                   │
    │◀── User ──────────────│                   │                   │
    │                       │                   │                   │
    │── get(id) ───────────▶│                   │                   │
    │                       │── HGET primary ──▶│                   │
    │                       │◀── miss: null ─────│                   │
    │                       │───────────────────────── find() ──────▶│
    │                       │◀───────────────────────── User ────────│
    │                       │── HSET primary ──▶│                   │
    │                       │── DEL volatile ───▶│                   │
    │◀── User ──────────────│                   │                   │
    │                       │                   │                   │
    │── set() ─────────────▶│                   │                   │
    │                       │──────────────────────── update() ─────▶│
    │                       │◀────────────────────── updatedUser ────│
    │                       │── HSET primary ──▶│                   │
    │                       │── DEL volatile ───▶│                   │
    │◀── updatedUser ───────│                   │                   │
```
