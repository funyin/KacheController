# MongoDB Adapter

`MongoKacheController` wraps the official [MongoDB Kotlin coroutines driver](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/).
Every method receives a `MongoCollection<T>` and a trailing lambda that executes directly against that collection.

## Setup

```kotlin
val cache: CacheClient = RedisCacheClient("redis://localhost:6379")
val controller = MongoKacheController(cache = cache)

val db = MongoClient.create("mongodb://localhost:27017").getDatabase("myApp")
val users: MongoCollection<User> = db.getCollection("users")
```

## Methods

```kotlin
// read one by id
suspend fun <T : Model> get(
    id: String,
    collection: MongoCollection<T>,
    serializer: KSerializer<T>,
    expire: Duration? = null,
    fieldExpire: Duration? = null,
    getData: suspend MongoCollection<T>.() -> T?,
): T?

// read many
suspend fun <T : Model> getAll(
    collection: MongoCollection<T>,
    serializer: KSerializer<T>,
    expire: Duration? = null,
    cacheKey: String = collection.cacheKey(),
    maxCacheSize: Int? = null,
    getData: suspend MongoCollection<T>.() -> List<T>,
): List<T>

// write one
suspend fun <T : Model> set(
    collection: MongoCollection<T>,
    cacheKey: String = collection.cacheKey(),
    serializer: KSerializer<T>,
    expire: Duration? = null,
    fieldExpire: Duration? = null,
    invalidateVolatiles: Boolean = true,
    setData: suspend MongoCollection<T>.() -> T?,
): T?

// write many
suspend fun <T : Model> setAll(
    collection: MongoCollection<T>,
    serializer: KSerializer<T>,
    expire: Duration? = null,
    fieldExpire: Duration? = null,
    invalidateVolatiles: Boolean = true,
    maxCacheSize: Int? = null,
    cacheKey: String = collection.cacheKey(),
    setData: suspend MongoCollection<T>.() -> List<T>?,
): Boolean

// cached volatile query result
suspend fun <T : Model, R : Any> getVolatile(
    fieldName: String,
    collection: MongoCollection<T>,
    serializer: KSerializer<R>,
    setData: suspend MongoCollection<T>.() -> R,
): R

// delete one
suspend fun <T : Model> remove(
    id: String,
    collection: MongoCollection<T>,
    deleteData: suspend MongoCollection<T>.() -> Boolean,
): Boolean

// delete many
suspend fun <T : Model> removeAll(
    collection: MongoCollection<T>,
    cacheKey: String = collection.cacheKey(),
    deleteData: suspend MongoCollection<T>.() -> Boolean,
): Boolean

// fire-and-forget write (requires asyncWriteScope)
suspend fun <T : Model> setAsync(...)
suspend fun <T : Model> setAllAsync(...)
```

## Example

```kotlin
// write-through upsert
val updated = controller.set(users, User.serializer()) {
    findOneAndUpdate(
        Filters.eq("_id", userId),
        Updates.set(User::firstName.name, "NewName"),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
    )
}

// read-through filtered query (result cached as volatile)
val admins = controller.getAll(
    users, User.serializer(),
    cacheKey = "users:role:admin",
) {
    find(Filters.eq(User::role.name, "admin")).toList()
}

// cached aggregate (count, sum, etc.)
val count = controller.getVolatile(
    "users:count",
    users,
    Long.serializer(),
) {
    countDocuments()
}
```
