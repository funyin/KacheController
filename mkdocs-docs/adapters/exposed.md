# Exposed Adapter

`ExposedKacheController` works with any database that [Jetbrains Exposed](https://github.com/JetBrains/Exposed) supports: PostgreSQL, MySQL, H2, SQLite, and others.

## Setup

```kotlin
val cache: CacheClient = RedisCacheClient("redis://localhost:6379")
val controller = ExposedKacheController(cache = cache)
```

Configure your Exposed `Database` connection separately — `ExposedKacheController` does not manage the connection.

## Table definition

Your Exposed table must be an `object` (or singleton) so the table reference is stable across calls.

```kotlin
object UsersTable : Table("users") {
    val id = varchar("id", 36)
    val firstName = varchar("first_name", 255)
    val lastName = varchar("last_name", 255)
    override val primaryKey = PrimaryKey(id)
}
```

The cache key is derived from `Table.tableName` (and `Table.schemaName` when set).

## Methods

```kotlin
suspend fun <T : Model> get(
    id: String,
    table: Table,
    serializer: KSerializer<T>,
    expire: Duration? = null,
    getData: suspend Table.() -> T?,
): T?

suspend fun <T : Model> getAll(
    table: Table,
    serializer: KSerializer<T>,
    expire: Duration? = null,
    cacheKey: String = table.cacheKey(),
    maxCacheSize: Int? = null,
    getData: suspend Table.() -> List<T>,
): List<T>

suspend fun <T : Model> set(
    table: Table,
    cacheKey: String = table.cacheKey(),
    serializer: KSerializer<T>,
    expire: Duration? = null,
    invalidateVolatiles: Boolean = true,
    setData: suspend Table.() -> T?,
): T?

suspend fun <T : Model> setAll(
    table: Table,
    serializer: KSerializer<T>,
    expire: Duration? = null,
    invalidateVolatiles: Boolean = true,
    maxCacheSize: Int? = null,
    cacheKey: String = table.cacheKey(),
    setData: suspend Table.() -> List<T>?,
): Boolean

suspend fun <R : Any> getVolatile(
    fieldName: String,
    table: Table,
    serializer: KSerializer<R>,
    setData: suspend Table.() -> R,
): R

suspend fun remove(
    id: String,
    table: Table,
    deleteData: suspend Table.() -> Boolean,
): Boolean

suspend fun removeAll(
    table: Table,
    cacheKey: String = table.cacheKey(),
    deleteData: suspend Table.() -> Boolean,
): Boolean
```

!!! warning
    `ExposedKacheController` does not support `fieldExpire` — per-field TTL is Redis-specific and not wired in the Exposed adapter. Use `expire` (whole-hash TTL) or rely on explicit eviction.

## Example

```kotlin
val user = controller.get(userId, UsersTable, User.serializer()) {
    select { id eq userId }.singleOrNull()?.toUser()
}

controller.setAll(UsersTable, User.serializer()) {
    batchInsert(newUsers) { u ->
        this[UsersTable.id] = u.id
        this[UsersTable.firstName] = u.firstName
        this[UsersTable.lastName] = u.lastName
    }.map { it.toUser() }
}

controller.remove(userId, UsersTable) {
    deleteWhere { id eq userId } > 0
}
```
