package com.funyinkash.kachecontroller

import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * @property cacheEnabled will be checked before checking cache,
 * you can change this to false at anytime if you don't want to hit the cache
 * @property asyncWriteScope required only when using [setAsync] or [setAllAsync]. Provides the
 * [CoroutineScope] under which the background write-queue consumer runs. When the scope is
 * cancelled the consumer stops and the channel is closed. Leave null (the default) unless you
 * need write-behind behaviour.
 * @property onAsyncWriteError called on the consumer coroutine whenever an async DB write throws.
 * Defaults to a no-op; supply a handler that logs, retries, or alerts when using [setAsync]/[setAllAsync].
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class KacheController(
    val cacheEnabled: () -> Boolean = { true },
    private val client: RedisCoroutinesCommands<String, String>,
    asyncWriteScope: CoroutineScope? = null,
    private val onAsyncWriteError: (Throwable) -> Unit = {},
) {

    private val logger = LoggerFactory.getLogger("KacheController")

    // Sentinel stored as a hash field to mark an empty-but-cached collection.
    private val EMPTY_SENTINEL = "__kache_empty__"

    // Unbounded channel so cache writes never block waiting for DB drain.
    // Only created when a scope is supplied; null acts as a compile-time guard against
    // accidental use of setAsync/setAllAsync without the required scope.
    private val writeQueue: Channel<suspend () -> Unit>? =
        asyncWriteScope?.let { Channel(Channel.UNLIMITED) }

    init {
        asyncWriteScope?.launch {
            writeQueue!!.consumeEach { write ->
                runCatching { write() }.onFailure(onAsyncWriteError)
            }
        }?.invokeOnCompletion { writeQueue?.close() }
    }

    /**
     * Get A single item from your db or cache </br>
     * ```kotlin
     *
     * ```
     * @param collection is the mongo collection you wan to perform action on
     * @param serializer is required to deserialize your object properly
     * @param expire sets the time to live for the collections cache after data is gotten from database
     * @param getData a context receiver that provides the collection for making query
     */
    suspend fun <T : Model> get(
        id: String,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        getData: suspend MongoCollection<T>.() -> T?,
    ): T? {
        if (!cacheEnabled()) return getData(collection)
        val cacheKey = collection.cacheKey()
        val data = client.hget(cacheKey, id)
        return if (data != null) {
            logger.info("CACHE HIT get $cacheKey")
            Json.decodeFromString(serializer, data)
        } else {
            logger.info("CACHE MISS get $cacheKey")
            val realData = getData(collection)
            set(collection, serializer = serializer, expire = expire, setData = {
                realData
            })
        }
    }

    /**
     * Get the items from the list if they exist else perform the real query
     * update the cache and return the results.
     *
     * Note: when a custom [cacheKey] is provided the cached results are stored as a regular hash
     * and are NOT automatically invalidated by [set] or [setAll]. Use [getVolatile] instead for
     * filtered queries whose results must stay consistent with writes.
     *
     * @param expire sets the time to live for the collections cache after data is gotten from database
     */
    suspend fun <T : Model> getAll(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        cacheKey: String = collection.cacheKey(),
        getData: suspend MongoCollection<T>.() -> List<T>,
    ): List<T> {
        if (!cacheEnabled())
            return getData(collection)
        val cacheExists = (client.exists(cacheKey) ?: 0L) > 0L
        return if (cacheExists) {
            logger.info("CACHE HIT getAll $cacheKey")
            streamLargeHash(cacheKey, serializer)
        } else {
            logger.info("CACHE MISS getAll $cacheKey")
            val realData = getData(collection)
            setAll(collection, serializer, expire = expire, cacheKey, setData = { realData })
            realData
        }
    }

    // Streams all fields of a Redis hash in pages of 100, skipping the empty sentinel field.
    private suspend fun <T : Model> streamLargeHash(
        key: String,
        serializer: KSerializer<T>,
    ): List<T> {
        val items = mutableListOf<T>()
        val scanArgs = ScanArgs().apply { limit(100) }
        var scanCursor: ScanCursor = ScanCursor.INITIAL
        while (true) {
            val scanResult = client.hscan(key, scanCursor, scanArgs) ?: break
            scanResult.map.entries.forEach { (field, json) ->
                if (field != EMPTY_SENTINEL) items.add(Json.decodeFromString(serializer, json))
            }
            scanCursor = scanResult
            if (scanResult.isFinished) break
        }
        return items
    }

    /**
     * Insert or update a single item in your db and return it.
     * This will update the item in the cache by the id
     * @param expire sets the time to live for the collections cache after data is put in database
     */
    suspend fun <T : Model> set(
        collection: MongoCollection<T>,
        cacheKey: String = collection.cacheKey(),
        serializer: KSerializer<T>,
        expire: Duration? = null,
        setData: suspend MongoCollection<T>.() -> T?,
    ): T? {
        if (!cacheEnabled()) return setData(collection)
        val realData = setData(collection)
        return if (realData != null) {
            val modelId = realData.id
            val response = client.hset(cacheKey, modelId, Json.encodeToString(serializer, realData))
            logger.info("CACHE SET set $cacheKey - newField:$response")
            clearVolatile(collection)
            expire?.let { client.expire(cacheKey, it) }
            realData
        } else null
    }

    /**
     * Insert or update multiple items in your db and return the updated items.
     * This will update their data in the cache buy their id
     *
     * ```kotlin
     * val db = mongoClient.getDatabase("kacheController")
     *     val usersCollection = db.getCollection<User>("users")
     *     val users = listOf(
     *         User(firstName = "Funyin", lastName = "Kashimawo"),
     *         User(firstName = "John", lastName = "Norris"),
     *         User(firstName = "Tyler", lastName = "Chidubem"),
     *     )
     *     controller.setAll(collection = usersCollection, User.serializer()) {
     *         if (insertMany(users).wasAcknowledged())
     *             users
     *         else
     *             emptyList()
     *     }
     * ```
     * @param expire sets the time to live for the collections cache after data is gotten from database
     */
    suspend fun <T : Model> setAll(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        cacheKey: String = collection.cacheKey(),
        setData: suspend MongoCollection<T>.() -> List<T>?,
    ): Boolean {
        if (!cacheEnabled()) return setData(collection) != null
        val realData = setData(collection) ?: return false
        val map = realData.associate { it.id to Json.encodeToString(serializer, it) }
        if (map.isNotEmpty()) {
            val response = client.hset(cacheKey, map)
            logger.info("CACHE SET setAll $cacheKey - $response")
        } else {
            // Store a sentinel so subsequent getAll calls recognise this as a cache hit.
            client.hset(cacheKey, EMPTY_SENTINEL, "1")
            logger.info("CACHE SET setAll $cacheKey - empty sentinel")
        }
        expire?.let { client.expire(cacheKey, it) }
        clearVolatile(collection)
        return true
    }

    /**
     * Volatiles are queries whose result depends on the state of the collection
     * i.e if an items is added, modified or deleted it'll affect the response of the query
     * e.g
     * ```kotlin
     *val key = "${keyPrefix}page:$page|size:$size"
     * val results = cacheController.getVolatile(
     *     key,
     *     collection,
     *     serializer = ListSerializer(elementSerializer = serializer)
     * ) {
     *     val skip = (page - 1) * size
     *     val list = arrayListOf<T>()
     *     list.addAll(getResults(skip).toList())
     *     list
     * }
     * val countKey = "${keyPrefix}pagesCount|size:$size"
     * val totalItems = cacheController.getVolatile(
     *     countKey,
     *     collection,
     *     serializer = Long.serializer()
     * ) {
     *     collection.estimatedDocumentCount().awaitFirst()
     * }
     * ```
     */
    suspend fun <T : Model, R : Any> getVolatile(
        fieldName: String,
        collection: MongoCollection<T>,
        serializer: KSerializer<R>,
        setData: suspend MongoCollection<T>.() -> R,
    ): R {
        if (!cacheEnabled()) return setData(collection)
        val cacheKey = collection.volatileCacheKey()
        val cache = client.hget(cacheKey, fieldName)
        return if (!cache.isNullOrEmpty()) {
            logger.info("CACHE HIT getVolatile $cacheKey")
            Json.decodeFromString(serializer, cache)
        } else {
            logger.info("CACHE MISS getVolatile $cacheKey")
            val realData = setData(collection)
            setVolatile(fieldName, collection, serializer, realData)
        }
    }

    /**
     * You shouldn't need to call this in your code since volatiles are not actually
     * stores in your db but are just results of queries from your db
     *
     * @see getVolatile
     */
    private suspend fun <T : Model, R : Any> setVolatile(
        fieldName: String, collection: MongoCollection<T>, serializer: KSerializer<R>, setData: R,
    ): R {
        if (!cacheEnabled()) return setData
        val cacheKey = collection.volatileCacheKey()
        val response = client.hset(cacheKey, fieldName, Json.encodeToString(serializer, setData))
        logger.info("CACHE SET setVolatile $cacheKey - $response")
        return setData
    }

    fun <T : Model> MongoCollection<T>.volatileCacheKey() = "${cacheKey()}:volatile"
    fun <T : Model> MongoCollection<T>.cacheKey() = "${namespace.databaseName}:${namespace.collectionName}"

    private suspend fun <T : Model> clearVolatile(collection: MongoCollection<T>) {
        client.del(collection.volatileCacheKey())
        logger.info("CACHE CLEAR VOLATILE ${collection.volatileCacheKey()}")
    }

    // -------------------------------------------------------------------------
    // Write-behind (fire-and-forget) — HIGH THROUGHPUT / NON-TRANSACTIONAL ONLY
    // -------------------------------------------------------------------------
    //
    // These methods are designed for a narrow set of workloads:
    //   • Usage metering / billing counters
    //   • Audit / telemetry logging
    //   • Any append-only write where losing an in-flight record on process crash
    //     is acceptable
    //
    // They MUST NOT be used for transactional data, anything user-facing that
    // requires read-your-own-write consistency, or anything where the DB is the
    // source of truth for in-flight records.
    //
    // Both methods require `asyncWriteScope` to have been passed at construction.
    // -------------------------------------------------------------------------

    /**
     * **FIRE-AND-FORGET — read the warning block above before using.**
     *
     * Writes [item] to the Redis cache synchronously, then enqueues the database
     * write for asynchronous execution. The caller returns as soon as the cache
     * write completes; the DB write happens in the background without blocking
     * the request path.
     *
     * Failure behaviour: if the DB write throws, [onAsyncWriteError] is invoked
     * on the consumer coroutine. There is no automatic retry — implement it in
     * [onAsyncWriteError] if your workload requires it.
     *
     * Data-loss risk: any writes still in the queue when the process exits are
     * lost. Do not use this for data that cannot be reconstructed.
     *
     * @throws IllegalStateException if [asyncWriteScope] was not supplied at construction.
     */
    suspend fun <T : Model> setAsync(
        item: T,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        writeData: suspend MongoCollection<T>.(T) -> Unit,
    ) {
        check(writeQueue != null) {
            "setAsync requires a CoroutineScope — pass asyncWriteScope to KacheController"
        }
        if (cacheEnabled()) {
            val cacheKey = collection.cacheKey()
            client.hset(cacheKey, item.id, Json.encodeToString(serializer, item))
            logger.info("CACHE SET setAsync $cacheKey - ${item.id}")
            clearVolatile(collection)
            expire?.let { client.expire(cacheKey, it) }
        }
        writeQueue.send { collection.writeData(item) }
    }

    /**
     * **FIRE-AND-FORGET — read the warning block above before using.**
     *
     * Writes all [items] to the Redis cache synchronously, then enqueues the
     * database write for asynchronous execution. Volatile results for the
     * collection are cleared immediately so subsequent reads reflect the new
     * cache state.
     *
     * See [setAsync] for failure behaviour and data-loss risk.
     *
     * @throws IllegalStateException if [asyncWriteScope] was not supplied at construction.
     */
    suspend fun <T : Model> setAllAsync(
        items: List<T>,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        expire: Duration? = null,
        writeData: suspend MongoCollection<T>.(List<T>) -> Unit,
    ) {
        check(writeQueue != null) {
            "setAllAsync requires a CoroutineScope — pass asyncWriteScope to KacheController"
        }
        if (cacheEnabled()) {
            val cacheKey = collection.cacheKey()
            val map = items.associate { it.id to Json.encodeToString(serializer, it) }
            if (map.isNotEmpty()) {
                client.hset(cacheKey, map)
            } else {
                client.hset(cacheKey, EMPTY_SENTINEL, "1")
            }
            logger.info("CACHE SET setAllAsync $cacheKey - ${items.size} items")
            expire?.let { client.expire(cacheKey, it) }
            clearVolatile(collection)
        }
        writeQueue.send { collection.writeData(items) }
    }

    /**
     * Delete an item from your db, if that was successful return [true] or [false]
     * if [true] the item is also deleted from the cache
     */
    suspend fun <T : Model> remove(
        id: String, collection: MongoCollection<T>, deleteData: suspend MongoCollection<T>.() -> Boolean,
    ): Boolean {
        if (!cacheEnabled())
            return deleteData(collection)
        val cacheKey = collection.cacheKey()
        return if (deleteData(collection)) {
            val response = client.hdel(cacheKey, id)
            logger.info("CACHE DROP remove $cacheKey - $response")
            true
        } else
            false
    }

    /**
     * Delete all the items in a collection, if that was successful return [true] or [false]
     * if [true] all the items in the cache will also be deleted
     * e.g
     * ```kotlin
     *kacheController.removeAll(collection) {
     *    collection.deleteMany(Filters.empty()).deletedCount > 0
     *}
     * ```
     * ensure that you use the delete count instead of `wasAcknowledged()` because that will still be true when
     * no items are deleted
     */
    suspend fun <T : Model> removeAll(
        collection: MongoCollection<T>,
        cacheKey: String = collection.cacheKey(),
        deleteData: suspend MongoCollection<T>.() -> Boolean,
    ): Boolean {
        if (!cacheEnabled())
            return deleteData(collection)
        return if (deleteData(collection)) {
            val fields = client.hkeys(cacheKey).toList().toTypedArray()
            if (fields.isNotEmpty()) {
                val response = client.hdel(cacheKey, *fields)
                logger.info("CACHE SET removeAll $cacheKey - $response")
            }
            true
        } else
            false
    }
}
