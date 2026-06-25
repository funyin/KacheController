# Write Operations

Write operations follow write-through: the database lambda runs first, then the result is stored in the cache. Volatile query results for the same collection are cleared automatically.

---

## `set` — write one document

```kotlin
val saved = controller.set(
    collection = usersCollection,
    serializer = User.serializer(),
    expire = null,
    fieldExpire = null,               // MongoDB only, Redis 7.4+
    invalidateVolatiles = true,       // set false to skip volatile clear
) {
    findOneAndUpdate(
        Filters.eq("_id", user.id),
        Updates.set(User::firstName.name, "NewName"),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
    )
}
```

The lambda must return the updated `Model` instance so the cache can store it. Returns `null` if the lambda returns `null`; the cache is not updated.

**`invalidateVolatiles = false`** — skip clearing volatile keys when you know the write cannot affect any cached query results (e.g. updating `lastSeen` while volatiles are status-count queries).

---

## `setAll` — write multiple documents

```kotlin
val ok = controller.setAll(
    collection = usersCollection,
    serializer = User.serializer(),
    invalidateVolatiles = true,
    maxCacheSize = null,              // skip caching if list exceeds this size
) {
    if (insertMany(users).wasAcknowledged()) users else emptyList()
}
```

Returns `true` when the lambda returns a non-null list (even if empty). An empty result writes a sentinel value (`__kache_empty__`) to the cache so subsequent `getAll` calls know the collection is genuinely empty rather than uncached.

**`maxCacheSize`** — if `result.size > maxCacheSize`, the result is returned but not written to cache.

---

## Cache key override

Both `set` and `setAll` accept an optional `cacheKey` parameter that overrides the default collection key. Use this when you need to store the same document under multiple lookup keys.

```kotlin
controller.set(users, User.serializer(), cacheKey = "users:by-email:$email") {
    findOneAndReplace(Filters.eq("email", email), updatedUser)
}
```
