# Read Operations

All read operations follow the same read-through pattern: check cache → on miss, call the database lambda → store result → return.

---

## `get` — read one by id

```kotlin
val user = controller.get(
    id = userId,
    collection = usersCollection,   // or table = UsersTable for Exposed
    serializer = User.serializer(),
    expire = null,           // optional: whole-hash TTL
    fieldExpire = null,      // optional: per-field TTL (MongoDB only, Redis 7.4+)
) {
    find(Filters.eq("_id", id)).firstOrNull()
}
```

**Cache hit** — `HGET <cacheKey> <id>`, deserialise, return.

**Cache miss** — call the lambda, store the result with `HSET <cacheKey> <id> <json>`, return.

Returns `null` when the database lambda returns `null`; nothing is written to the cache in that case.

---

## `getAll` — read a collection

```kotlin
val users = controller.getAll(
    collection = usersCollection,
    serializer = User.serializer(),
    expire = null,
    cacheKey = usersCollection.cacheKey(),   // default: collection cache key
    maxCacheSize = null,                      // skip caching if result exceeds this size
) {
    find().toList()
}
```

**Default `cacheKey`** — uses `HGETALL` on the collection hash. Each document is a separate field, so individual writes/deletes can evict specific fields without flushing the whole list.

**Custom `cacheKey`** — the result is stored as a single JSON array inside the collection's volatile hash. It is automatically invalidated whenever `set`, `setAll`, `remove`, or `removeAll` runs on the same collection. See [Keys & Volatiles](../keys_and_volatiles.md).

**`maxCacheSize`** — when set, the result is returned from DB but not written to cache if `result.size > maxCacheSize`. Prevents caching huge result sets.

---

## `getVolatile` — cached computed value

For query results that are not simple document lists (counts, aggregates, paginated slices):

```kotlin
val count = controller.getVolatile(
    fieldName = "users:count",
    collection = usersCollection,
    serializer = Long.serializer(),
) {
    countDocuments()
}
```

Stored as a field inside the collection's volatile hash — automatically invalidated on any write to the same collection.
