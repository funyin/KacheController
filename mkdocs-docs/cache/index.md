# Cache Backends

KacheController ships three cache backends and accepts any custom implementation of `CacheClient`.

| Backend | Class | External dependency | Persistence |
|---|---|---|---|
| Redis | `RedisCacheClient` | Lettuce | Shared, survives restarts |
| In-memory | `InMemoryCacheClient` | None | Process-local, lost on restart |
| SQLite | `SQLiteCacheClient` | sqlite-jdbc | File-based, single-process |

## Choosing a backend

- **Redis** — the right default for production: shared across instances, survives restarts, supports per-field TTL (Redis 7.4+), and cluster-ready.
- **In-memory** — integration tests, local development, or single-process workloads where a Redis dependency is undesirable.
- **SQLite** — persistent local cache for single-process services where Redis is unavailable (e.g. edge deployments).

## Implementing your own

`CacheClient` is a small interface. All methods are `suspend` and hash-based, modelling the Redis hash data type.

```kotlin
interface CacheClient {
    suspend fun hget(key: String, field: String): String?
    suspend fun hset(key: String, field: String, value: String): Boolean
    suspend fun hset(key: String, entries: Map<String, String>): Long
    suspend fun hdel(key: String, vararg fields: String): Long
    suspend fun del(vararg keys: String): Long
    suspend fun exists(key: String): Boolean
    suspend fun hgetAll(key: String): Map<String, String>

    // optional — default implementations are no-ops
    suspend fun expire(key: String, ttl: Duration) {}
    suspend fun hexpire(key: String, ttl: Duration, vararg fields: String) {}
}
```

`expire` and `hexpire` are optional. The default no-op implementations degrade gracefully: TTL parameters passed to controller methods are silently ignored if the backend does not support them.
