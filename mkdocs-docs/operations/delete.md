# Delete Operations

Delete operations run the database lambda first. On success they evict the affected cache entry and clear all volatile keys for the collection.

---

## `remove` — delete one document

```kotlin
val deleted = controller.remove(
    id = userId,
    collection = usersCollection,
) {
    deleteOne(Filters.eq("_id", userId)).wasAcknowledged()
}
```

Returns `true` when the lambda returns `true`. On success:

- `HDEL <cacheKey> <id>` — evicts the specific document field.
- `DEL <volatileKey>` — clears all volatile query results for the collection.

---

## `removeAll` — delete many documents

```kotlin
val deleted = controller.removeAll(
    collection = usersCollection,
) {
    deleteMany(Filters.`in`("_id", ids)).deletedCount > 0
}
```

Uses `DEL <cacheKey>` (a single atomic command) to drop the entire collection hash, then clears volatiles. This is safe under concurrent reads — there is no window between field-by-field eviction and cache inconsistency.

**`cacheKey` override** — pass a custom key when the collection is stored under a non-default key:

```kotlin
controller.removeAll(users, cacheKey = "users:by-role:admin") {
    deleteMany(Filters.eq("role", "admin")).deletedCount > 0
}
```
