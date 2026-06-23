package com.funyinkash.kachecontroller

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration as JavaDuration
import kotlin.time.toKotlinDuration

/**
 * Abstract base class implementing read-through/write-through caching logic.
 *
 * Subclasses provide the typed public API for a specific database backend
 * (e.g. [MongoKacheController], [ExposedKacheController]).
 *
 * @param cacheEnabled  Called before every operation; when `false` the cache is bypassed.
 * @param cache         The [CacheClient] backend (Redis, in-memory, SQLite, etc.).
 * @param asyncWriteScope  If set, enables write-behind via [setAsyncInternal]/[setAllAsyncInternal].
 * @param onAsyncWriteError  Optional handler for errors caught during async write execution.
 */
abstract class KacheController(
    val cacheEnabled: () -> Boolean = { true },
    protected val cache: CacheClient,
    asyncWriteScope: CoroutineScope? = null,
    private val onAsyncWriteError: (Throwable) -> Unit = {},
) {

    protected val logger = LoggerFactory.getLogger("KacheController")

    private val EMPTY_SENTINEL = "__kache_empty__"

    private val writeQueue: Channel<suspend () -> Unit>? =
        asyncWriteScope?.let { Channel(Channel.UNLIMITED) }

    init {
        asyncWriteScope?.launch {
            writeQueue!!.consumeEach { write ->
                runCatching { write() }.onFailure(onAsyncWriteError)
            }
        }?.invokeOnCompletion { writeQueue?.close() }
    }

    // ── Internal cache helpers ────────────────────────────────────────────────

    /**
     * Read through: fetch from cache or delegate to [getData] and populate cache on miss.
     * Also invalidates any volatile keys so stale custom-query results are dropped.
     */
    protected suspend fun <T : Model> getInternal(
        id: String,
        cacheKey: String,
        volatileKey: String,
        serializer: KSerializer<T>,
        expire: JavaDuration? = null,
        fieldExpire: JavaDuration? = null,
        getData: suspend () -> T?,
    ): T? {
        if (!cacheEnabled()) return getData()
        val data = cache.hget(cacheKey, id)
        return if (data != null) {
            logger.info("CACHE HIT get $cacheKey")
            Json.decodeFromString(serializer, data)
        } else {
            logger.info("CACHE MISS get $cacheKey")
            val realData = getData()
            if (realData != null) {
                cache.hset(cacheKey, realData.id, Json.encodeToString(serializer, realData))
                logger.info("CACHE SET get $cacheKey - ${realData.id}")
                cache.del(volatileKey)
                logger.info("CACHE CLEAR VOLATILE $volatileKey")
                expire?.let { cache.expire(cacheKey, it.toKotlinDuration()) }
                fieldExpire?.let { cache.hexpire(cacheKey, it.toKotlinDuration(), realData.id) }
            }
            realData
        }
    }

    /**
     * Read through for collection results.
     * - Default [cacheKey] → stores individual fields, serves from `HGETALL`.
     * - Custom [cacheKey] → stored in volatile hash, invalidated on writes.
     * - [maxCacheSize] prevents caching collections that exceed the given size.
     */
    protected suspend fun <T : Model> getAllInternal(
        collectionCacheKey: String,
        volatileKey: String,
        serializer: KSerializer<T>,
        expire: JavaDuration? = null,
        cacheKey: String = collectionCacheKey,
        maxCacheSize: Int? = null,
        getData: suspend () -> List<T>,
    ): List<T> {
        if (!cacheEnabled()) return getData()

        val isDefaultKey = cacheKey == collectionCacheKey

        if (isDefaultKey) {
            val cacheExists = cache.exists(collectionCacheKey)
            return if (cacheExists) {
                logger.info("CACHE HIT getAll $collectionCacheKey")
                cache.hgetAll(collectionCacheKey).entries
                    .filter { it.key != EMPTY_SENTINEL }
                    .map { (_, json) -> Json.decodeFromString(serializer, json) }
            } else {
                logger.info("CACHE MISS getAll $collectionCacheKey")
                val realData = getData()
                if (maxCacheSize == null || realData.size <= maxCacheSize) {
                    setAllInternal(
                        collectionCacheKey, volatileKey, serializer,
                        expire = expire,
                        setData = { realData },
                    )
                }
                realData
            }
        } else {
            val cached = cache.hget(volatileKey, cacheKey)
            return if (cached != null) {
                logger.info("CACHE HIT getAll $cacheKey (volatile)")
                Json.decodeFromString(ListSerializer(serializer), cached)
            } else {
                logger.info("CACHE MISS getAll $cacheKey (volatile)")
                val realData = getData()
                if (maxCacheSize == null || realData.size <= maxCacheSize) {
                    cache.hset(volatileKey, cacheKey, Json.encodeToString(ListSerializer(serializer), realData))
                    logger.info("CACHE SET getAll $cacheKey (volatile)")
                }
                realData
            }
        }
    }

    /** Write through: persist via [setData], then cache the result. */
    protected suspend fun <T : Model> setInternal(
        cacheKey: String,
        volatileKey: String,
        serializer: KSerializer<T>,
        expire: JavaDuration? = null,
        fieldExpire: JavaDuration? = null,
        invalidateVolatiles: Boolean = true,
        setData: suspend () -> T?,
    ): T? {
        if (!cacheEnabled()) return setData()
        val realData = setData()
        if (realData != null) {
            val modelId = realData.id
            cache.hset(cacheKey, modelId, Json.encodeToString(serializer, realData))
            logger.info("CACHE SET set $cacheKey - $modelId")
            if (invalidateVolatiles) clearVolatileInternal(volatileKey)
            expire?.let { cache.expire(cacheKey, it.toKotlinDuration()) }
            fieldExpire?.let { cache.hexpire(cacheKey, it.toKotlinDuration(), modelId) }
        }
        return realData
    }

    /** Write through for bulk inserts. Stores an empty sentinel when the result list is empty. */
    protected suspend fun <T : Model> setAllInternal(
        cacheKey: String,
        volatileKey: String,
        serializer: KSerializer<T>,
        expire: JavaDuration? = null,
        fieldExpire: JavaDuration? = null,
        invalidateVolatiles: Boolean = true,
        maxCacheSize: Int? = null,
        setData: suspend () -> List<T>?,
    ): Boolean {
        if (!cacheEnabled()) return setData() != null
        val realData = setData() ?: return false
        val map = realData.associate { it.id to Json.encodeToString(serializer, it) }
        if (maxCacheSize != null && realData.size > maxCacheSize) return true
        if (map.isNotEmpty()) {
            cache.hset(cacheKey, map)
            logger.info("CACHE SET setAll $cacheKey - ${map.size} fields")
        } else {
            cache.hset(cacheKey, EMPTY_SENTINEL, "1")
            logger.info("CACHE SET setAll $cacheKey - empty sentinel")
        }
        expire?.let { cache.expire(cacheKey, it.toKotlinDuration()) }
        if (invalidateVolatiles) clearVolatileInternal(volatileKey)
        fieldExpire?.let { cache.hexpire(cacheKey, it.toKotlinDuration(), *map.keys.toList().toTypedArray()) }
        return true
    }

    /**
     * Read through for a custom volatile value stored at a named field within the volatile hash.
     * Returns a cached value or computes via [setData] and caches the result.
     */
    protected suspend fun <R : Any> getVolatileInternal(
        fieldName: String,
        volatileKey: String,
        serializer: KSerializer<R>,
        setData: suspend () -> R,
    ): R {
        if (!cacheEnabled()) return setData()
        val cacheVal = cache.hget(volatileKey, fieldName)
        return if (!cacheVal.isNullOrEmpty()) {
            logger.info("CACHE HIT getVolatile $volatileKey")
            Json.decodeFromString(serializer, cacheVal)
        } else {
            logger.info("CACHE MISS getVolatile $volatileKey")
            val realData = setData()
            cache.hset(volatileKey, fieldName, Json.encodeToString(serializer, realData))
            logger.info("CACHE SET setVolatile $volatileKey")
            realData
        }
    }

    /** Remove a single item: delete from DB, evict from cache, clear volatiles. */
    protected suspend fun removeInternal(
        id: String,
        cacheKey: String,
        volatileKey: String,
        deleteData: suspend () -> Boolean,
    ): Boolean {
        if (!cacheEnabled()) return deleteData()
        return if (deleteData()) {
            cache.hdel(cacheKey, id)
            logger.info("CACHE DROP remove $cacheKey - $id")
            clearVolatileInternal(volatileKey)
            true
        } else false
    }

    /** Remove all matching items: delete from DB, evict the entire cache key, clear volatiles. */
    protected suspend fun removeAllInternal(
        cacheKey: String,
        volatileKey: String,
        deleteData: suspend () -> Boolean,
    ): Boolean {
        if (!cacheEnabled()) return deleteData()
        return if (deleteData()) {
            cache.del(cacheKey)
            logger.info("CACHE DROP removeAll $cacheKey")
            clearVolatileInternal(volatileKey)
            true
        } else false
    }

    // ── Write-behind (fire-and-forget) ───────────────────────────────────────

    protected suspend fun <T : Model> setAsyncInternal(
        item: T,
        cacheKey: String,
        volatileKey: String,
        serializer: KSerializer<T>,
        expire: JavaDuration? = null,
        fieldExpire: JavaDuration? = null,
        writeData: suspend () -> Unit,
    ) {
        check(writeQueue != null) {
            "setAsync requires a CoroutineScope — pass asyncWriteScope to KacheController"
        }
        if (cacheEnabled()) {
            cache.hset(cacheKey, item.id, Json.encodeToString(serializer, item))
            logger.info("CACHE SET setAsync $cacheKey - ${item.id}")
            clearVolatileInternal(volatileKey)
            expire?.let { cache.expire(cacheKey, it.toKotlinDuration()) }
            fieldExpire?.let { cache.hexpire(cacheKey, it.toKotlinDuration(), item.id) }
        }
        writeQueue.send(writeData)
    }

    protected suspend fun <T : Model> setAllAsyncInternal(
        items: List<T>,
        cacheKey: String,
        volatileKey: String,
        serializer: KSerializer<T>,
        expire: JavaDuration? = null,
        fieldExpire: JavaDuration? = null,
        writeData: suspend () -> Unit,
    ) {
        check(writeQueue != null) {
            "setAllAsync requires a CoroutineScope — pass asyncWriteScope to KacheController"
        }
        if (cacheEnabled()) {
            val map = items.associate { it.id to Json.encodeToString(serializer, it) }
            if (map.isNotEmpty()) {
                cache.hset(cacheKey, map)
            } else {
                cache.hset(cacheKey, EMPTY_SENTINEL, "1")
            }
            logger.info("CACHE SET setAllAsync $cacheKey - ${items.size} items")
            clearVolatileInternal(volatileKey)
            expire?.let { cache.expire(cacheKey, it.toKotlinDuration()) }
            fieldExpire?.let { cache.hexpire(cacheKey, it.toKotlinDuration(), *map.keys.toList().toTypedArray()) }
        }
        writeQueue.send(writeData)
    }

    // ── Internal helpers ─────────────────────────────────────────────────────

    /** Delete a volatile key, invalidating all custom query results stored there. */
    protected suspend fun clearVolatileInternal(volatileKey: String) {
        cache.del(volatileKey)
        logger.info("CACHE CLEAR VOLATILE $volatileKey")
    }
}
