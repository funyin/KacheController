# Keys & Volatiles

Understanding how KacheController derives and manages cache keys is important for debugging cache misses and designing custom query caching.

## Primary cache key

Each adapter derives one canonical key per database collection or table:

| Adapter | Key format | Example |
|---|---|---|
| MongoDB | `"<databaseName>:<collectionName>"` | `"myApp:users"` |
| Exposed (no schema) | `"<tableName>"` | `"users"` |
| Exposed (with schema) | `"<schemaName>:<tableName>"` | `"public:users"` |

The primary key is a Redis hash. Each field in the hash is a document `id`; the value is the JSON-serialised document.

## Volatile key

The volatile key is always `"<primaryKey>:volatile"` (e.g. `"myApp:users:volatile"`). It is a second Redis hash that stores custom query results:

- Paginated result sets (a specific page of users)
- Filtered lists (users with `role = "admin"`)
- Aggregates (count of active users)

### How volatile values are stored

When `getAll` is called with a custom `cacheKey`, the result is stored as `HSET <volatileKey> <cacheKey> <jsonArray>`.

When `getVolatile` is called with a `fieldName`, the result is stored as `HSET <volatileKey> <fieldName> <json>`.

### Automatic invalidation

`DEL <volatileKey>` is issued after any write or delete operation (`set`, `setAll`, `remove`, `removeAll`, `setAsync`, `setAllAsync`). This ensures that cached query results are never stale after a mutation.

The entire volatile hash is dropped atomically in one command — there is no partial invalidation.

## Skipping volatile invalidation

Pass `invalidateVolatiles = false` to `set` or `setAll` when you are certain the write cannot affect any cached query result:

```kotlin
// Updating lastSeen does not affect any status-count queries
controller.set(users, User.serializer(), invalidateVolatiles = false) {
    findOneAndUpdate(filter, Updates.set("lastSeen", Instant.now()))
}
```

## Empty-collection sentinel

When `setAll` returns an empty list, KacheController writes a sentinel field `__kache_empty__` to the primary hash. This tells subsequent `getAll` calls that the collection is genuinely empty, preventing a cache miss on every read. The sentinel is filtered out before results are returned to the caller.

## Visualising the key structure

```
myApp:users               ← primary hash
  abc123  → {"id":"abc123","firstName":"Alice",...}
  def456  → {"id":"def456","firstName":"Bob",...}

myApp:users:volatile      ← volatile hash
  users:role:admin        → [{"id":"def456",...}]
  users:count             → 2
```
