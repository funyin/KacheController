# Why KacheController

## The problem

Adding a cache to a repository layer is repetitive: check cache → miss → fetch DB → store in cache → return. Every method needs the same structure, the same serialisation boilerplate, and the same invalidation logic duplicated in every write. Bugs in one place silently diverge from another.

## What KacheController does differently

KacheController inverts the relationship: instead of wrapping a cache client in your repository, you wrap your database lambda in a controller call. The read-through and write-through logic lives once, in the library.

```kotlin
// without KacheController
suspend fun getUser(id: String): User? {
    val cached = redis.hget("myApp:users", id)
    if (cached != null) return json.decodeFromString(cached)
    val user = db.find(id)
    if (user != null) redis.hset("myApp:users", id, json.encodeToString(user))
    return user
}

// with KacheController
suspend fun getUser(id: String): User? =
    controller.get(id, usersCollection, User.serializer()) { find(id) }
```

## Cache backend separation

`CacheClient` is a thin interface. Swapping Redis for in-memory (in tests) or SQLite (in offline scenarios) requires changing one constructor argument, not the repository code.

## Volatile query results

Filtered lists and aggregates are stored in a dedicated volatile hash and invalidated atomically on any write. There is no manual invalidation call to forget.

## Comparison

| Feature | Manual caching | KacheController |
|---|---|---|
| Read-through | Hand-coded per method | Built in |
| Write-through | Hand-coded per method | Built in |
| Volatile invalidation | Manual, error-prone | Automatic |
| Cache backend swap | Invasive refactor | Constructor argument |
| Serialisation | Repeated boilerplate | One serializer argument |
| Empty collection handling | Often missed | Built-in sentinel |
| Write-behind | Non-trivial to implement safely | `setAsync` / `setAllAsync` |

## How it works

Each controller method:

1. Checks `cacheEnabled()` — if false, calls the database lambda directly.
2. Reads from the cache backend using `hget` or `hgetAll`.
3. On hit: deserialises and returns immediately.
4. On miss: calls the database lambda, serialises the result, stores it with `hset`, then returns.
5. For writes: stores the result, clears the volatile hash with `DEL`.

All serialisation uses `kotlinx-serialization` JSON. Cache keys are deterministic strings derived from the database schema — no configuration required.
