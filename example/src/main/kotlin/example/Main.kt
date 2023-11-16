package example

import KacheController
import Model
import com.mongodb.client.model.Filters
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.api.coroutines
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.bson.types.ObjectId


@OptIn(ExperimentalLettuceCoroutinesApi::class)
suspend fun main(args: Array<String>) {
    val mongoClient = MongoClient.create("mongodb://localhost:27016")
    val redisClient = RedisClient.create("redis://127.0.0.1:6379")
    val connection = redisClient.connect()
    val coroutinesCommands = connection.coroutines()
    val controller = KacheController(
        cacheEnabled = { true },
        client = coroutinesCommands,
    )

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
    controller.getAll(usersCollection, User.serializer()) {
        find().toList()
    }.also {
        println(it)
    }

    controller.set(usersCollection, User.serializer()) {
        findOneAndUpdate(
            Filters.eq("_id", users.first().id),
            Updates.set(User::firstName.name, "NewNameFirst")
        )
    }.also {
        println(it)
    }
    controller.get(users.first().id, usersCollection, User.serializer()) {
        find(Filters.eq(User::id.name, users.first().id)).firstOrNull()
    }.also {
        println(it)
    }

}

@Serializable
private data class User(
    @SerialName("_id")
    override val id: String = ObjectId().toHexString(),
    val firstName: String,
    val lastName: String,
) : Model {
    //OR
//    override val id: String
//        get() = ""
}