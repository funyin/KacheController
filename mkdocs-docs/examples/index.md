# Examples

---

## Basic read-through / write-through (MongoDB + Redis)

```kotlin
val cache = RedisCacheClient("redis://localhost:6379")
val controller = MongoKacheController(cache = cache)

val db = MongoClient.create("mongodb://localhost:27017").getDatabase("myApp")
val users = db.getCollection<User>("users")

// write-through insert
controller.setAll(users, User.serializer()) {
    if (insertMany(newUsers).wasAcknowledged()) newUsers else emptyList()
}

// read-through get-all
val all = controller.getAll(users, User.serializer()) {
    find().toList()
}
```

---

## Filtered list with volatile caching

```kotlin
val admins = controller.getAll(
    users, User.serializer(),
    cacheKey = "users:role:admin",
) {
    find(Filters.eq("role", "admin")).toList()
}

// When any user is updated, the "users:role:admin" entry is invalidated automatically.
controller.set(users, User.serializer()) {
    findOneAndUpdate(filter, update, options)
}
```

---

## Cached aggregate

```kotlin
val activeCount = controller.getVolatile(
    fieldName = "users:status:active:count",
    collection = users,
    serializer = Long.serializer(),
) {
    countDocuments(Filters.eq("status", "active"))
}
```

---

## Per-field TTL (Redis 7.4+)

```kotlin
controller.set(
    sessions, Session.serializer(),
    fieldExpire = Duration.ofHours(24),
) {
    findOneAndReplace(Filters.eq("_id", session.id), session)
}
```

---

## Skipping volatile invalidation

```kotlin
// lastSeen update does not affect any status or count queries
controller.set(users, User.serializer(), invalidateVolatiles = false) {
    findOneAndUpdate(
        Filters.eq("_id", userId),
        Updates.set("lastSeen", Instant.now()),
        FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER),
    )
}
```

---

## Capping cache size

```kotlin
// Do not cache if the result has more than 500 documents
val results = controller.getAll(
    users, User.serializer(),
    maxCacheSize = 500,
) {
    find().toList()
}
```

---

## Write-behind

```kotlin
val controller = MongoKacheController(
    cache = cache,
    asyncWriteScope = CoroutineScope(Dispatchers.IO),
    onAsyncWriteError = { logger.error("Async write failed", it) },
)

// Cache is updated immediately; DB write is queued
controller.setAsync(updatedUser, users, User.serializer()) { user ->
    replaceOne(Filters.eq("_id", user.id), user)
}
```

---

## SQL database with Exposed

```kotlin
val cache = InMemoryCacheClient()
val controller = ExposedKacheController(cache = cache)

val user = controller.get(userId, UsersTable, User.serializer()) {
    select { id eq userId }.singleOrNull()?.toUser()
}

controller.remove(userId, UsersTable) {
    deleteWhere { id eq userId } > 0
}
```

---

## Runtime cache toggle

```kotlin
var cacheOn = true

val controller = MongoKacheController(
    cacheEnabled = { cacheOn },
    cache = cache,
)

// Disable cache for a request (e.g. admin bypass)
cacheOn = false
val freshData = controller.getAll(users, User.serializer()) { find().toList() }
cacheOn = true
```
