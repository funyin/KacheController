package com.funyinkash.kachecontroller

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import org.jetbrains.exposed.sql.Table
import java.time.Duration

/**
 * PostgreSQL adapter providing a typed public API over [KacheController].
 *
 * Each method delegates to [KacheController]'s internal helpers, deriving cache keys
 * from the [Table] schema and table names.
 */
class PostgresKacheController(
    cacheEnabled: () -> Boolean = { true },
    cache: CacheClient,
    asyncWriteScope: CoroutineScope? = null,
    onAsyncWriteError: (Throwable) -> Unit = {},
) : KacheController(cacheEnabled, cache, asyncWriteScope, onAsyncWriteError) {

    private fun Table.cacheKey(): String =
        schemaName?.let { "$it:$tableName" } ?: tableName

    private fun Table.volatileKey(): String = "${cacheKey()}:volatile"

    suspend fun <T : Model> get(
        id: String,
        table: Table,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        getData: suspend Table.() -> T?,
    ): T? = getInternal(
        id, table.cacheKey(), table.volatileKey(),
        serializer, expire, null,
        getData = { table.getData() },
    )

    suspend fun <T : Model> getAll(
        table: Table,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        cacheKey: String = table.cacheKey(),
        maxCacheSize: Int? = null,
        getData: suspend Table.() -> List<T>,
    ): List<T> = getAllInternal(
        table.cacheKey(), table.volatileKey(),
        serializer, expire, cacheKey, maxCacheSize,
        getData = { table.getData() },
    )

    suspend fun <T : Model> set(
        table: Table,
        cacheKey: String = table.cacheKey(),
        serializer: KSerializer<T>,
        expire: Duration? = null,
        invalidateVolatiles: Boolean = true,
        setData: suspend Table.() -> T?,
    ): T? = setInternal(
        cacheKey, table.volatileKey(),
        serializer, expire, null, invalidateVolatiles,
        setData = { table.setData() },
    )

    suspend fun <T : Model> setAll(
        table: Table,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        invalidateVolatiles: Boolean = true,
        maxCacheSize: Int? = null,
        cacheKey: String = table.cacheKey(),
        setData: suspend Table.() -> List<T>?,
    ): Boolean = setAllInternal(
        cacheKey, table.volatileKey(),
        serializer, expire, null, invalidateVolatiles, maxCacheSize,
        setData = { table.setData() },
    )

    suspend fun <R : Any> getVolatile(
        fieldName: String,
        table: Table,
        serializer: KSerializer<R>,
        setData: suspend Table.() -> R,
    ): R = getVolatileInternal(
        fieldName, table.volatileKey(),
        serializer,
        setData = { table.setData() },
    )

    suspend fun remove(
        id: String,
        table: Table,
        deleteData: suspend Table.() -> Boolean,
    ): Boolean = removeInternal(
        id, table.cacheKey(), table.volatileKey(),
        deleteData = { table.deleteData() },
    )

    suspend fun removeAll(
        table: Table,
        cacheKey: String = table.cacheKey(),
        deleteData: suspend Table.() -> Boolean,
    ): Boolean = removeAllInternal(
        cacheKey, table.volatileKey(),
        deleteData = { table.deleteData() },
    )
}
