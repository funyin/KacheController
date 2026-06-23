![Maven Central](https://img.shields.io/maven-central/v/com.funyinkash.kachecontroller/kachecontroller-core)

## KacheController

A pluggable read-through/write-through cache layer for database operations.
Swap any cache backend with any database adapter — no boilerplate.

### Modules

| Module | Artifact | Purpose |
|---|---|---|
| `kachecontroller-core` | `kachecontroller-core` | Abstract `CacheClient` interface + `KacheController` base class |
| `kachecontroller-cache-redis` | `kachecontroller-cache-redis` | Redis `CacheClient` via Lettuce |
| `kachecontroller-cache-memory` | `kachecontroller-cache-memory` | In-memory `CacheClient` (no external deps) |
| `kachecontroller-cache-sqlite` | `kachecontroller-cache-sqlite` | SQLite `CacheClient` via sqlite-jdbc |
| `kachecontroller-mongo` | `kachecontroller-mongo` | `MongoKacheController` — MongoDB adapter |
| `kachecontroller-exposed` | `kachecontroller-exposed` | `ExposedKacheController` — any Exposed-compatible DB |

### Usage

Pick one **cache** and one **database** adapter:

```kotlin
// Cache backends
val memCache: CacheClient = InMemoryCacheClient()
val sqliteCache: CacheClient = SQLiteCacheClient.create("jdbc:sqlite:kache.db")
val redisCache: CacheClient = RedisCacheClient("redis://localhost:6379")

// Database adapters
val mongoCtrl = MongoKacheController(cache = redisCache)
val pgCtrl    = ExposedKacheController(cache = redisCache)
```

Your models implement `Model` (`val id: String`) and use `kotlinx-serialization`:

```kotlin
@Serializable
data class User(
    @SerialName("_id")
    override val id: String = ObjectId().toHexString(),
    val firstName: String,
    val lastName: String,
) : Model
```

See the [example module](example/) for a complete runnable demo.

### Design

- **Cache keys:** `"<dbName>:<collectionName>"`; volatiles = `"$key:volatile"`.
- **Custom `getAll` keys** are volatile — auto-invalidated on writes.
- **Empty collections** store a `__kache_empty__` sentinel (filtered from results).
- **`removeAll`** uses `DEL` (atomic), not `HKEYS`+`HDEL`.
- **Write-behind** (`setAsync`/`setAllAsync`): fire-and-forget, not for transactional data.

### License

Apache 2.0
