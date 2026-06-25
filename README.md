![Maven Central](https://img.shields.io/maven-central/v/com.funyinkash.kachecontroller/kachecontroller-core)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)

# KacheController

A pluggable read-through/write-through cache layer for Kotlin database operations.
Pick a cache backend, pick a database adapter, wrap your queries — no boilerplate.

**[Documentation](https://funyin.github.io/KacheController/) · [KDoc](https://funyin.github.io/KacheController/kdoc/latest/) · [Releases](https://github.com/funyin/KacheController/releases)**

---

## Modules

| Module | Artifact | Purpose |
|---|---|---|
| `kachecontroller-core` | `kachecontroller-core` | `Model`, `CacheClient` interface, `KacheController` base |
| `kachecontroller-cache-redis` | `kachecontroller-cache-redis` | Redis backend via Lettuce |
| `kachecontroller-cache-memory` | `kachecontroller-cache-memory` | In-memory backend (no external deps) |
| `kachecontroller-cache-sqlite` | `kachecontroller-cache-sqlite` | SQLite backend via sqlite-jdbc |
| `kachecontroller-mongo` | `kachecontroller-mongo` | `MongoKacheController` — MongoDB adapter |
| `kachecontroller-exposed` | `kachecontroller-exposed` | `ExposedKacheController` — any Exposed-compatible DB |

---

## Installation

Pick one **cache backend** and one **database adapter**. `kachecontroller-core` is pulled in transitively.

Replace `LATEST_VERSION` with the version from the badge above.

**Kotlin Gradle**

```kotlin
dependencies {
    // cache backend — pick one
    implementation("com.funyinkash:kachecontroller-cache-redis:LATEST_VERSION")
    // implementation("com.funyinkash:kachecontroller-cache-memory:LATEST_VERSION")
    // implementation("com.funyinkash:kachecontroller-cache-sqlite:LATEST_VERSION")

    // database adapter — pick one
    implementation("com.funyinkash:kachecontroller-mongo:LATEST_VERSION")
    // implementation("com.funyinkash:kachecontroller-exposed:LATEST_VERSION")
}
```

**Groovy Gradle**

```groovy
dependencies {
    implementation 'com.funyinkash:kachecontroller-cache-redis:LATEST_VERSION'
    implementation 'com.funyinkash:kachecontroller-mongo:LATEST_VERSION'
}
```

**Maven**

```xml
<dependency>
    <groupId>com.funyinkash</groupId>
    <artifactId>kachecontroller-cache-redis-jvm</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
<dependency>
    <groupId>com.funyinkash</groupId>
    <artifactId>kachecontroller-mongo-jvm</artifactId>
    <version>LATEST_VERSION</version>
</dependency>
```

Also add the `kotlinx-serialization` plugin — all models must be `@Serializable`:

```kotlin
plugins {
    kotlin("plugin.serialization") version "1.9.0"
}
```

---

## Quick Start

### 1. Define a model

```kotlin
@Serializable
data class User(
    @SerialName("_id")
    override val id: String = ObjectId().toHexString(),
    val firstName: String,
    val lastName: String,
) : Model
```

### 2. Wire up a cache and controller

```kotlin
val cache = RedisCacheClient("redis://localhost:6379")
val controller = MongoKacheController(cache = cache)
```

### 3. Use the controller

```kotlin
val db = mongoClient.getDatabase("myApp")
val users = db.getCollection<User>("users")

// write-through
controller.set(users, User.serializer()) {
    findOneAndUpdate(filter, update, options)
}

// read-through
val user = controller.get(userId, users, User.serializer()) {
    find(Filters.eq("_id", userId)).firstOrNull()
}

// bulk write-through
controller.setAll(users, User.serializer()) {
    if (insertMany(newUsers).wasAcknowledged()) newUsers else emptyList()
}

// bulk read-through
val allUsers = controller.getAll(users, User.serializer()) {
    find().toList()
}

// filtered query — result cached as volatile, auto-invalidated on writes
val admins = controller.getAll(users, User.serializer(), cacheKey = "users:role:admin") {
    find(Filters.eq("role", "admin")).toList()
}

// delete and evict
controller.remove(userId, users) {
    deleteOne(Filters.eq("_id", userId)).wasAcknowledged()
}
```

See the [example module](example/) for a complete runnable demo with MongoDB and Redis.

---

## How It Works

Every collection gets two Redis hashes:

```
"myApp:users"          ← primary hash — one field per document (id → json)
"myApp:users:volatile" ← volatile hash — cached query results (key → json array)
```

- **Read operations** check the cache first; on miss they call your lambda, store the result, and return.
- **Write operations** call your lambda first, then update the cache with the confirmed result and clear the volatile hash.
- **Volatile keys** (filtered lists, aggregates, counts) live in the volatile hash and are invalidated automatically on any write — no manual tracking.

Full breakdown: [How It Works](https://funyin.github.io/KacheController/latest/how_it_works/)

---

## Cache Backends

| Backend | Class | Persistence | TTL support |
|---|---|---|---|
| Redis | `RedisCacheClient` | Shared, survives restarts | Full (per-field via `HEXPIRE`, Redis 7.4+) |
| In-memory | `InMemoryCacheClient` | Process-local | None |
| SQLite | `SQLiteCacheClient` | File-based | None |

Implement `CacheClient` to bring your own backend.

---

## Design Notes

- **Cache keys** — `"<dbName>:<collectionName>"` (MongoDB) or `"<schema>:<table>"` (Exposed).
- **Custom `getAll` keys** are volatile — stored in the volatile hash, auto-invalidated on writes.
- **Empty collections** write a `__kache_empty__` sentinel so repeated `getAll` calls don't hit the DB.
- **`removeAll`** uses `DEL` (atomic single command) to drop the entire collection hash.
- **Write-behind** (`setAsync`/`setAllAsync`) — updates cache immediately, queues the DB write. Not for transactional data.
- **`cacheEnabled`** — a `() -> Boolean` evaluated on every call; wire to a feature flag for runtime toggling.

---

## Documentation

Full documentation including installation, API reference, examples, and internal architecture:

**[funyin.github.io/KacheController](https://funyin.github.io/KacheController/)**

KDoc (generated API reference):

**[funyin.github.io/KacheController/kdoc/latest/](https://funyin.github.io/KacheController/kdoc/latest/)**

---

## License

[Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0)
