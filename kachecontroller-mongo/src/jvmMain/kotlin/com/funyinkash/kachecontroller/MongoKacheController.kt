package com.funyinkash.kachecontroller

import com.mongodb.kotlin.client.coroutine.MongoCollection
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.KSerializer
import java.time.Duration

/**
 * MongoDB adapter providing a typed public API over [KacheController].
 *
 * Each method delegates to [KacheController]'s internal helpers, deriving cache keys
 * from the database and collection names in the [MongoCollection] namespace.
 */
class MongoKacheController(
    cacheEnabled: () -> Boolean = { true },
    cache: CacheClient,
    asyncWriteScope: CoroutineScope? = null,
    onAsyncWriteError: (Throwable) -> Unit = {},
) : KacheController(cacheEnabled, cache, asyncWriteScope, onAsyncWriteError) {

    private fun MongoCollection<*>.cacheKey(): String =
        "${namespace.databaseName}:${namespace.collectionName}"

    private fun MongoCollection<*>.volatileKey(): String = "${cacheKey()}:volatile"

    suspend fun <T : Model> get(
        id: String,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        fieldExpire: Duration? = null,
        getData: suspend MongoCollection<T>.() -> T?,
    ): T? = getInternal(
        id, collection.cacheKey(), collection.volatileKey(),
        serializer, expire, fieldExpire,
        getData = { collection.getData() },
    )

    suspend fun <T : Model> getAll(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        cacheKey: String = collection.cacheKey(),
        maxCacheSize: Int? = null,
        getData: suspend MongoCollection<T>.() -> List<T>,
    ): List<T> = getAllInternal(
        collection.cacheKey(), collection.volatileKey(),
        serializer, expire, cacheKey, maxCacheSize,
        getData = { collection.getData() },
    )

    suspend fun <T : Model> set(
        collection: MongoCollection<T>,
        cacheKey: String = collection.cacheKey(),
        serializer: KSerializer<T>,
        expire: Duration? = null,
        fieldExpire: Duration? = null,
        invalidateVolatiles: Boolean = true,
        setData: suspend MongoCollection<T>.() -> T?,
    ): T? = setInternal(
        cacheKey, collection.volatileKey(),
        serializer, expire, fieldExpire, invalidateVolatiles,
        setData = { collection.setData() },
    )

    suspend fun <T : Model> setAll(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        fieldExpire: Duration? = null,
        invalidateVolatiles: Boolean = true,
        maxCacheSize: Int? = null,
        cacheKey: String = collection.cacheKey(),
        setData: suspend MongoCollection<T>.() -> List<T>?,
    ): Boolean = setAllInternal(
        cacheKey, collection.volatileKey(),
        serializer, expire, fieldExpire, invalidateVolatiles, maxCacheSize,
        setData = { collection.setData() },
    )

    suspend fun <T : Model, R : Any> getVolatile(
        fieldName: String,
        collection: MongoCollection<T>,
        serializer: KSerializer<R>,
        setData: suspend MongoCollection<T>.() -> R,
    ): R = getVolatileInternal(
        fieldName, collection.volatileKey(),
        serializer,
        setData = { collection.setData() },
    )

    suspend fun <T : Model> remove(
        id: String,
        collection: MongoCollection<T>,
        deleteData: suspend MongoCollection<T>.() -> Boolean,
    ): Boolean = removeInternal(
        id, collection.cacheKey(), collection.volatileKey(),
        deleteData = { collection.deleteData() },
    )

    suspend fun <T : Model> removeAll(
        collection: MongoCollection<T>,
        cacheKey: String = collection.cacheKey(),
        deleteData: suspend MongoCollection<T>.() -> Boolean,
    ): Boolean = removeAllInternal(
        cacheKey, collection.volatileKey(),
        deleteData = { collection.deleteData() },
    )

    suspend fun <T : Model> setAsync(
        item: T,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        fieldExpire: Duration? = null,
        writeData: suspend MongoCollection<T>.(T) -> Unit,
    ) = setAsyncInternal(
        item, collection.cacheKey(), collection.volatileKey(),
        serializer, expire, fieldExpire,
        writeData = { collection.writeData(item) },
    )

    suspend fun <T : Model> setAllAsync(
        items: List<T>,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        fieldExpire: Duration? = null,
        writeData: suspend MongoCollection<T>.(List<T>) -> Unit,
    ) = setAllAsyncInternal(
        items, collection.cacheKey(), collection.volatileKey(),
        serializer, expire, fieldExpire,
        writeData = { collection.writeData(items) },
    )
}
