# AGENTS.md

## Project

Multi-module Kotlin (JVM) library providing a pluggable read-through/write-through cache over database backends.

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `kachecontroller-core` | `kachecontroller-core` | Abstract `CacheClient` interface + `KacheController` base class |
| `kachecontroller-cache-redis` | `kachecontroller-cache-redis` | Redis `CacheClient` via Lettuce |
| `kachecontroller-cache-memory` | `kachecontroller-cache-memory` | In-memory `CacheClient` (no external deps) |
| `kachecontroller-cache-sqlite` | `kachecontroller-cache-sqlite` | SQLite `CacheClient` via sqlite-jdbc |
| `kachecontroller-mongo` | `kachecontroller-mongo` | `MongoKacheController` — MongoDB adapter |
| `kachecontroller-exposed` | `kachecontroller-exposed` | `ExposedKacheController` — any Exposed-compatible DB |
| `example` | — | Demo app (needs local MongoDB:27017 + Redis:6379) |

## Commands

```bash
./gradlew build                                                    # build all modules + example
./gradlew :kachecontroller-core:jvmTest                            # core + contract tests
./gradlew :kachecontroller-cache-memory:jvmTest                    # in-memory cache tests
./gradlew :kachecontroller-cache-sqlite:jvmTest                    # SQLite cache tests
./gradlew :kachecontroller-cache-redis:jvmTest                     # Redis cache tests (MockK)
./gradlew :kachecontroller-mongo:jvmTest                           # Mongo adapter tests (37)
./gradlew :kachecontroller-exposed:jvmTest                        # Exposed adapter tests (MockK)
./gradlew :example:run                                             # run demo (needs DBs locally)
./gradlew dokkaHtmlMultiModule                                     # build docs → docs/
```

## Architecture

**`CacheClient`** (`kachecontroller-core`) — swappable cache backend. Methods: `hget`, `hset`, `hdel`, `del`, `exists`, `hgetAll`, `expire`, `hexpire`.

**`KacheController`** (abstract, `kachecontroller-core`) — all caching logic. Uses `CacheClient` internally. Exposes `protected` helper methods that take raw string keys. Subclasses (e.g. `MongoKacheController`) provide the typed public API.

**Models** implement `Model` (`val id: String`). Fields serialized as JSON via `kotlinx-serialization`.

**Cache keys:** `"<dbName>:<collectionName>"`; volatiles = `"$key:volatile"`.

## Critical design decisions

- **`internal` visibility does NOT cross Gradle module boundaries.** All base class helper methods in `KacheController` are `protected`.
- **Custom `getAll` keys are volatile.** Non-default `cacheKey` → results go into the volatile hash. `set`/`setAll`/`remove`/`removeAll` auto-invalidate them.
- **`removeAll` uses `DEL`** (single round-trip, atomic), not `HKEYS`+`HDEL`.
- **Empty collections** store a `__kache_empty__` sentinel field so subsequent `getAll` hits the cache (sentinel filtered from results).
- **DB write is inside the lambda**, not the controller. The controller caches whatever the lambda returns.
- **`removeAll` condition:** use `deleteMany(...).deletedCount > 0`, **not** `wasAcknowledged()` (the latter returns `true` even when nothing was deleted, dropping the cache incorrectly).
- **`fieldExpire`** (per-doc TTL via `HEXPIRE`) requires Redis 7.4+; degrades gracefully with a one-time warning logged by `RedisCacheClient`.
- **Write-behind (`setAsync`/`setAllAsync`):** fire-and-forget. Requires `asyncWriteScope` at construction. Cache write is synchronous, DB write is enqueued. Throws `IllegalStateException` without scope. Not for transactional data.

## Volatile invalidation matrix

| Method | Clears volatiles? | Controllable? |
|---|---|---|
| `set` | Yes | `invalidateVolatiles = false` |
| `setAll` | Yes | `invalidateVolatiles = false` |
| `remove` | Yes (on success) | No |
| `removeAll` | Yes (on success) | No |
| `get` / `getAll` / `getVolatile` | No | — |

## Publishing

Credentials and signing config live in `local.properties` (project root, gitignored), **not** `gradle.properties`. Requires `osshr.username`, `osshr.password`, `signing.secretKeyFile` (relative path), `signing.password`.

Publish all modules: `./gradlew publishToMavenLocal` (no `:mongo-redis` subproject exists anymore — that alias was removed).
