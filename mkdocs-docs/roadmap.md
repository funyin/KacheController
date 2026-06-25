# Roadmap

## Done

- `kachecontroller-core` — abstract `CacheClient` + `KacheController`
- `kachecontroller-cache-redis` — Redis backend via Lettuce
- `kachecontroller-cache-memory` — in-memory backend (no external deps)
- `kachecontroller-cache-sqlite` — SQLite backend via sqlite-jdbc
- `kachecontroller-mongo` — MongoDB adapter
- `kachecontroller-exposed` — Exposed adapter (PostgreSQL, MySQL, H2, SQLite)
- Per-field TTL via `HEXPIRE` (Redis 7.4+)
- Write-behind (`setAsync` / `setAllAsync`)
- Volatile query result caching and automatic invalidation
- Empty-collection sentinel to prevent repeated cache misses
- `maxCacheSize` guard on `getAll` / `setAll`
- Published to Maven Central

## Planned

- Additional cache backends (Memcached, DynamoDB)
- Kotlin Multiplatform support for cache backends
- Metrics / instrumentation hooks (hit rate, miss rate, eviction count)
- TTL support for SQLite and in-memory backends
