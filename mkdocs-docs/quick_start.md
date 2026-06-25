# Quick Start

## 1. Define a model

```kotlin
@Serializable
data class User(
    @SerialName("_id")
    override val id: String = ObjectId().toHexString(),
    val firstName: String,
    val lastName: String,
) : Model
```

## 2. Create a cache client

```kotlin
// in-memory — no external dependencies
val cache: CacheClient = InMemoryCacheClient()

// Redis — requires a running Redis instance
val cache: CacheClient = RedisCacheClient("redis://localhost:6379")

// SQLite — persistent, single-process
val cache: CacheClient = SQLiteCacheClient.create("jdbc:sqlite:kache.db")
```

## 3. Create a controller

```kotlin
// MongoDB
val controller = MongoKacheController(cache = cache)

// Any Exposed-compatible database
val controller = ExposedKacheController(cache = cache)
```

## 4. Use the controller

```kotlin
val db = mongoClient.getDatabase("myApp")
val users = db.getCollection<User>("users")

// write-through set
controller.set(users, User.serializer()) {
    findOneAndUpdate(filter, update)
}

// read-through get
val user = controller.get(id, users, User.serializer()) {
    find(Filters.eq("_id", id)).firstOrNull()
}

// bulk write-through
controller.setAll(users, User.serializer()) {
    if (insertMany(newUsers).wasAcknowledged()) newUsers else emptyList()
}

// bulk read-through
val allUsers = controller.getAll(users, User.serializer()) {
    find().toList()
}

// delete and evict
controller.remove(id, users) {
    deleteOne(Filters.eq("_id", id)).wasAcknowledged()
}
```

!!! tip
    See the [example module](https://github.com/funyin/KacheController/tree/master/example) for a complete runnable demo with MongoDB and Redis.
