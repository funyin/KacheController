import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * @property cacheEnabled will be checked before checking cache,
 * you can change this to false at anytime if you don't want to hit the cache
 */

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class KacheController(
    val cacheEnabled: () -> Boolean = { true },
    private val client: RedisCoroutinesCommands<String, String>,
) {

    private val logger = LoggerFactory.getLogger("KacheController")

    /**
     * Get A single item from your db or cache </br>
     * ```kotlin
     *
     * ```
     * @param collection is the mongo collection you wan to perform action on
     * @param serializer is required to deserialize your object properly
     * @param getData a context receiver that provides the collection for making query
     */
    suspend fun <T : Model> get(
        id: String,
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
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
            set(collection, serializer, setData = { realData })
        }
    }

    /**
     * Get the items from the list if they exist else perform the real query
     * update the cache and return the results
     */

    suspend fun <T : Model> getAll(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        cacheKey: String = collection.cacheKey(),
        getData: suspend MongoCollection<T>.() -> List<T>,
    ): List<T> {
        if (!cacheEnabled())
            return getData(collection)
        val data = client.hvals(cacheKey).toList()
        return if (data.isNotEmpty()) {
            logger.info("CACHE HIT getAll $cacheKey")
            data.map { Json.decodeFromString(serializer, it) }
        } else {
            logger.info("CACHE MISS getAll $cacheKey")
            val realData = getData(collection)
            setAll(collection, serializer, cacheKey, setData = { realData })
            realData
        }
    }

    /**
     * Insert or update a single item in your db and return it.
     * This will update the item in the cache by the id
     */
    suspend fun <T : Model> set(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        setData: suspend MongoCollection<T>.() -> T?,
    ): T? {
        if (!cacheEnabled()) return setData(collection)
        val cacheKey = collection.cacheKey()
        val realData = setData(collection)
        return if (realData != null) {
            val modelId = realData.id
            val response = client.hset(cacheKey, modelId, Json.encodeToString(serializer, realData))
            logger.info("CACHE SET set $cacheKey - newField:$response")
            clearVolatile(collection)
            get(id = modelId, collection = collection, serializer = serializer) { null }
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
     */
    suspend fun <T : Model> setAll(
        collection: MongoCollection<T>,
        serializer: KSerializer<T>,
        cacheKey: String = collection.cacheKey(),
        setData: suspend MongoCollection<T>.() -> List<T>?,
    ): Boolean {
        if (!cacheEnabled()) return setData(collection) != null
        val realData = setData(collection)
        val map = realData!!.associate { it.id to Json.encodeToString(serializer, it) }
        if (map.isNotEmpty()) {
            val response = client.hset(cacheKey, map)
            logger.info("CACHE SET setAll $cacheKey - $response")
        }
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
        val cacheKey = collection.volatileCashKey()
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
        val cacheKey = collection.volatileCashKey()
        val response = client.hset(cacheKey, fieldName, Json.encodeToString(serializer, setData))
        logger.info("CACHE SET setVolatile $cacheKey - $response")
        return getVolatile(fieldName, collection, serializer) {
            setData
        }
    }

    fun <T : Model> MongoCollection<T>.volatileCashKey() = "${cacheKey()}:volatile"
    fun <T : Model> MongoCollection<T>.cacheKey() = "${namespace.databaseName}:${namespace.collectionName}"

    private suspend fun <T : Model> clearVolatile(collection: MongoCollection<T>) {
        client.del(collection.volatileCashKey())
        logger.info("CACHE CLEAR VOLATILE ${collection.volatileCashKey()}")
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
        collection: MongoCollection<T>, deleteData: suspend MongoCollection<T>.() -> Boolean,
    ): Boolean {
        if (!cacheEnabled())
            return deleteData(collection)
        val cacheKey = collection.cacheKey()
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