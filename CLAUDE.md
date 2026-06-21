# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

KacheController is a Kotlin library that provides a caching layer on top of database operations, eliminating boilerplate for read-through/write-through cache patterns. The library is published to Maven Central under `com.funyinkash.kachecontroller`.

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

All domain models must implement `Model` (in `mongo-redis/src/main/kotlin/com/funyinkash/kachecontroller/Model.kt`):

```kotlin
interface Model {
    val id: String  // used as the Redis hash field key
}
```

### `KacheController` (the only public API class)

Located at `mongo-redis/src/main/kotlin/com/funyinkash/kachecontroller/KacheController.kt`. Constructed with:
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
| `getAll` | `HSCAN` (streams in batches of 100) | `HSET` all on miss | No |
| `set` | — | `HSET` single field | Yes |
| `setAll` | — | `HSET` map | Yes |
| `remove` | — | `HDEL` single field | No |
| `removeAll` | — | `HDEL` all keys | No |
| `getVolatile` | `HGET` from volatile hash | `HSET` to volatile on miss | No |

**Volatiles** are collection-scoped cached query results (e.g. paginated results, counts) that are automatically invalidated whenever `set` or `setAll` runs on that collection. Use `getVolatile` for queries where results change when the collection changes.

### Publishing

Publishing requires these gradle properties (typically in `~/.gradle/gradle.properties`):
- `osshr.username` / `osshr.password` — OSSHR credentials
- `signing.secretKeyFile` — path relative to project root to a PGP key file
- `signing.password` — PGP key passphrase

Version is set in `mongo-redis/build.gradle.kts`. Append `-SNAPSHOT` to publish to the snapshots repository; omit it to publish to staging.
