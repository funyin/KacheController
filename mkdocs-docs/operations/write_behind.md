# Write-Behind

`setAsync` and `setAllAsync` update the cache immediately, then enqueue the database write to run in the background. This reduces perceived write latency when the caller does not need to wait for the database.

!!! warning
    Write-behind is fire-and-forget. If the database write fails, the cache already reflects the new value. Do not use for transactional data or when the write result must be confirmed before proceeding.

## Setup

Pass a `CoroutineScope` when constructing the controller. The scope drives the internal write queue:

```kotlin
val controller = MongoKacheController(
    cache = cache,
    asyncWriteScope = CoroutineScope(Dispatchers.IO),
    onAsyncWriteError = { throwable ->
        logger.error("Async write failed", throwable)
    },
)
```

Without `asyncWriteScope`, calling `setAsync` or `setAllAsync` throws `IllegalStateException`.

## `setAsync` — write one document asynchronously

```kotlin
controller.setAsync(
    item = updatedUser,
    collection = usersCollection,
    serializer = User.serializer(),
) { user ->
    replaceOne(Filters.eq("_id", user.id), user)
}
```

1. Caches `updatedUser` immediately.
2. Clears volatile keys immediately.
3. Enqueues the lambda for execution in `asyncWriteScope`.

## `setAllAsync` — write many documents asynchronously

```kotlin
controller.setAllAsync(
    items = updatedUsers,
    collection = usersCollection,
    serializer = User.serializer(),
) { users ->
    bulkWrite(users.map { ReplaceOneModel(Filters.eq("_id", it.id), it) })
}
```

Same behaviour as `setAsync` but for a list of documents.

## Error handling

Errors thrown inside the write lambda are caught and forwarded to `onAsyncWriteError`. The queue continues processing subsequent writes — a single failure does not stop the queue.
