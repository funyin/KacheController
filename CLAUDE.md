# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KacheController is a Kotlin library that provides a caching layer on top of database operations, eliminating boilerplate for read-through/write-through cache patterns. Published to Maven Central.

**Maven coordinates:** `com.funyinkash:kachecontroller-mongo-redis:1.0.5`  
**Docs:** https://funyin.github.io/KacheController/mongo-redis/index.html

The project is a multi-module Gradle build:
- **`mongo-redis/`** — the published library module (MongoDB + Redis via Lettuce)
- **`example/`** — runnable demo showing all API operations (requires local MongoDB on 27017 and Redis on 6379)

## Build & Run Commands

```bash
# Build all modules
./gradlew build

# Run the example app (requires MongoDB and Redis running locally)
./gradlew :example:run

# Build docs (Dokka HTML, output goes to docs/)
./gradlew dokkaHtmlMultiModule

# Publish to local Maven repository (for local testing)
./gradlew :mongo-redis:publishToMavenLocal

# Publish snapshot to OSSHR (requires osshr.username, osshr.password, signing keys in gradle.properties)
./gradlew :mongo-redis:publish
```

There are no tests currently (test dependency is commented out in `mongo-redis/build.gradle.kts`).

## Architecture

### Core abstraction

All domain models must implement `Model` (in `mongo-redis/src/jvmMain/kotlin/com/funyinkash/kachecontroller/Model.kt`):

```kotlin
interface Model {
    val id: String  // used as the Redis hash field key
}
```

### `KacheController` (the only public API class)

Located at `mongo-redis/src/jvmMain/kotlin/com/funyinkash/kachecontroller/KacheController.kt`. Constructed with:
- `cacheEnabled: () -> Boolean` — evaluated at call time; lets callers toggle caching at runtime
- `client: RedisCoroutinesCommands<String, String>` — Lettuce coroutines client

**Cache key scheme:**
- Per-collection hash: `"<databaseName>:<collectionName>"` (e.g. `"kacheController:users"`)
- Volatile results: `"<databaseName>:<collectionName>:volatile"`

**Storage format:** Redis hashes where field = `model.id`, value = JSON-serialized object via `kotlinx-serialization`.

**Operations and their cache behavior:**
| Method | Cache read | Cache write | Clears volatiles? |
|---|---|---|---|
| `get` | `HGET` by id | `HSET` on miss | No |
| `getAll` | `HSCAN` (streams in batches of 100) for default key; `HGET` from volatile hash for custom key | `HSET` all on miss (default key); `HSET` to volatile hash (custom key) | No |
| `set` | — | `HSET` single field | Yes (controllable via `invalidateVolatiles`) |
| `setAll` | — | `HSET` map | Yes (controllable via `invalidateVolatiles`) |
| `remove` | — | `HDEL` single field | Yes |
| `removeAll` | — | `DEL` entire hash (atomic) | Yes |
| `getVolatile` | `HGET` from volatile hash | `HSET` to volatile on miss | No |

**Volatiles** are collection-scoped cached query results (e.g. paginated results, counts) that are automatically invalidated whenever `set` or `setAll` runs on that collection. Use `getVolatile` for queries where results change when the collection changes.

### Key design decisions & learnings

- **Custom `getAll` keys are volatile.** Passing a non-default `cacheKey` to `getAll` stores the result as a field inside the collection's volatile hash. This means `set`/`setAll`/`remove`/`removeAll` auto-invalidate it — the caller doesn't need to manage invalidation. Old behaviour (persistent standalone hash) is gone; relying on it was a bug.
- **`set`/`setAll` can skip volatile clearing.** Pass `invalidateVolatiles = false` when the write is known not to affect query results (e.g. updating a `lastSeen` field when volatiles are status-count queries).
- **`getAll`/`setAll` can cap cache size.** Pass `maxCacheSize` to skip writing to cache when the result set is too large. The data is still returned from DB; it just bypasses the cache.
- **Per-document TTL via `fieldExpire`.** Requires Redis 7.4+ and Lettuce 6.5+. Uses `HEXPIRE` after each `HSET`. Degrades gracefully with a one-time warning on older Redis. Bumped Lettuce from 6.3.0 → 6.5.0 to support this.
- **`removeAll` is atomic.** Uses `DEL` on the whole hash (single round-trip, no race window) instead of `HKEYS` + `HDEL`.
- **`remove`/`removeAll` clear volatiles.** Deletions invalidate cached query results, just like writes do.

### Publishing

Publishing requires these gradle properties (typically in `~/.gradle/gradle.properties`):
- `osshr.username` / `osshr.password` — OSSHR credentials
- `signing.secretKeyFile` — path relative to project root to a PGP key file
- `signing.password` — PGP key passphrase

Version is set in `mongo-redis/build.gradle.kts`. Append `-SNAPSHOT` to publish to the snapshots repository; omit it to publish to staging.
