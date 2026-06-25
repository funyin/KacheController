# SQLite Cache

`SQLiteCacheClient` stores hash data in a local SQLite file via `sqlite-jdbc`. Useful when Redis is unavailable but persistence across restarts is needed.

## Setup

```kotlin
val cache: CacheClient = SQLiteCacheClient.create("jdbc:sqlite:kache.db")
```

Pass any JDBC URL accepted by sqlite-jdbc, including `:memory:` for an in-memory SQLite database.

## Characteristics

- **Persistent** — data survives process restarts (file-based).
- **Single-process** — SQLite has file-level locking; do not share the same `.db` file across multiple JVM processes concurrently.
- **No TTL support** — `expire` and `hexpire` are no-ops.

!!! warning
    `SQLiteCacheClient` is not suitable for multi-instance deployments. Use Redis when running more than one application instance.
