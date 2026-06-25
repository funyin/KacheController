# Database Adapters

A database adapter wraps `KacheController`'s internal read-through / write-through logic and exposes a typed API bound to a specific database client.

| Adapter | Class | Database |
|---|---|---|
| MongoDB | `MongoKacheController` | MongoDB (via the official Kotlin coroutines driver) |
| Exposed | `ExposedKacheController` | Any Exposed-compatible DB (PostgreSQL, MySQL, H2, SQLite, …) |

## Constructing an adapter

Both adapters share the same constructor parameters:

```kotlin
MongoKacheController(
    cacheEnabled: () -> Boolean = { true },
    cache: CacheClient,
    asyncWriteScope: CoroutineScope? = null,     // required for setAsync / setAllAsync
    onAsyncWriteError: (Throwable) -> Unit = {},  // optional error handler for write-behind
)
```

`cacheEnabled` is evaluated on every call, so you can toggle caching at runtime (e.g. from a feature flag or environment variable) without recreating the controller.

## Cache key derivation

Each adapter derives the cache key from its native schema object:

- **MongoDB** — `"<databaseName>:<collectionName>"` from `MongoCollection.namespace`
- **Exposed** — `"<schemaName>:<tableName>"` from `Table.schemaName` and `Table.tableName` (schema prefix omitted when null)

Volatile query results are stored under `"<cacheKey>:volatile"`.
