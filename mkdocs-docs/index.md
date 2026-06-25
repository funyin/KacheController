---
comments: true
---

# KacheController

A pluggable read-through / write-through cache layer for database operations.
Swap any cache backend with any database adapter — no boilerplate.

=== "MongoDB + Redis"

    ```kotlin
    val cache = RedisCacheClient("redis://localhost:6379")
    val controller = MongoKacheController(cache = cache)

    // read-through: fetches from cache, falls back to MongoDB on miss
    val user = controller.get(id, usersCollection, User.serializer()) {
        find(Filters.eq("_id", id)).firstOrNull()
    }
    ```

=== "SQL (Exposed) + In-Memory"

    ```kotlin
    val cache = InMemoryCacheClient()
    val controller = ExposedKacheController(cache = cache)

    // write-through: persists to DB, then updates cache
    val saved = controller.set(UsersTable, User.serializer()) {
        insertAndReturn(user)
    }
    ```

=== "Model definition"

    ```kotlin
    @Serializable
    data class User(
        override val id: String = ObjectId().toHexString(),
        val firstName: String,
        val lastName: String,
    ) : Model
    ```

---

**Cache backends** — Redis, in-memory, or SQLite. Bring your own by implementing `CacheClient`.

**Database adapters** — MongoDB and any Exposed-compatible database (PostgreSQL, MySQL, H2, SQLite).

**Zero boilerplate** — the cache key, read-through, write-through, and volatile invalidation logic live in the library, not your repositories.
