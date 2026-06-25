package example

import com.funyinkash.kachecontroller.CacheClient
import com.funyinkash.kachecontroller.Model
import com.funyinkash.kachecontroller.MongoKacheController
import com.funyinkash.kachecontroller.cache.InMemoryCacheClient
import com.funyinkash.kachecontroller.cache.RedisCacheClient
import com.funyinkash.kachecontroller.cache.SQLiteCacheClient
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId

suspend fun main(args: Array<String>) {
    // ── Cache backend variants ──────────────────────────────────────────
    //
    // In-memory (no external deps, data lost on restart):
    // val cache: CacheClient = InMemoryCacheClient()
    //
    // SQLite (persistent, single-process):
    // val cache: CacheClient = SQLiteCacheClient.create("jdbc:sqlite:kache.db")
    //
    // Redis (shared, multi-process — requires local Redis on :6379):
    val cache: CacheClient = RedisCacheClient("redis://127.0.0.1:6379")

    // ── Database adapter variants ───────────────────────────────────────
    //
    // MongoDB (requires local MongoDB on :27017):
    //   MongoKacheController(cache = cache)
    //
    // Any Exposed-compatible database (PostgreSQL, MySQL, H2, SQLite, etc.):
    //   ExposedKacheController(cache = cache)
    //
    // Example below uses MongoDB + Redis:

    val controller = MongoKacheController(
        cacheEnabled = { true },
        cache = cache,
    )

    val mongoClient = MongoClient.create("mongodb://localhost:27017")
    val db = mongoClient.getDatabase("kacheController")
    val usersCollection = db.getCollection<User>("users")
    val users = listOf(
        User(firstName = "Funyin", lastName = "Kashimawo"),
        User(firstName = "John", lastName = "Norris"),
        User(firstName = "Tyler", lastName = "Chidubem"),
    )

    controller.setAll(collection = usersCollection, User.serializer()) {
        if (insertMany(users).wasAcknowledged())
            users
        else
            emptyList()
    }
    controller.getAll(usersCollection, User.serializer(), cacheKey = "all:firsName:Funyin") {
        find(Filters.eq(User::firstName.name, "Funyin")).toList()
    }.also { println(it) }

    controller.set(usersCollection, serializer = User.serializer()) {
        findOneAndUpdate(
            Filters.eq("_id", users.first().id),
            Updates.set(User::firstName.name, "NewNameFirst"),
        )
    }.also { println(it) }
    controller.get(users.first().id, usersCollection, User.serializer()) {
        find(Filters.eq(User::id.name, users.first().id)).firstOrNull()
    }.also { println(it) }

    controller.remove(users.first().id, usersCollection) {
        deleteOne(Filters.eq("_id", users.first().id)).wasAcknowledged()
    }
    controller.removeAll(usersCollection) {
        deleteMany(
            Filters.or(users.map { Filters.eq("_id", it.id) }),
        ).deletedCount > 0
    }
}

@Serializable
private data class User(
    @SerialName("_id")
    override val id: String = ObjectId().toHexString(),
    val firstName: String,
    val lastName: String,
) : Model
