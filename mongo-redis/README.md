![Maven Central](https://img.shields.io/maven-central/v/com.funyinkash.kachecontroller/mongo-redis)


The mongo-redis use case makes use of
- **[lettuce](https://lettuce.io/)** for redis and
- **[mongodb kotlin driver](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/)** for the database
- **[Kiotlinx serialization json](https://www.mongodb.com/docs/drivers/kotlin/coroutine/current/)** to write and read objects in the cache

### Add Dependencies
```kotlin
dependencies{
	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")  
	implementation("io.lettuce:lettuce-core:6.2.2.RELEASE")
	implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
	
	//mongo-redis
	implementation("com.funyinkash.kachecontroller:mongo-redis:1.0.0")
}
```

### Setup
```kotlin
val mongoClient = MongoClient.create("mongodb://localhost:27017")  
val redisClient = RedisClient.create("redis://localhost:6379")  
val connection = redisClient.connect()  
val coroutinesCommands = connection.coroutines()  
val controller = KacheController(  
    cacheEnabled = {  
  true  
  },  
    client = coroutinesCommands  
)
```

### Models
To be able to make use of KacheController your models for your collections must extend [Model](src/main/kotlin/com/funyinkash/kachecontroller/Model.kt)

```kotlin
@Serializable  
private data class User(  
    @SerialName("_id")  
    override val id: String = ObjectId().toHexString(),  
    val firstName: String,  
    val lastName: String,  
) : Model
```

### Actions
First of all setup up your collections
```kotlin
val db = mongoClient.getDatabase("kacheController")  
val usersCollection = db.getCollection<User>("users")  
val users = listOf(  
    User(firstName = "Funyin", lastName = "Kashimawo"),  
    User(firstName = "John", lastName = "Norris"),  
    User(firstName = "Tyler", lastName = "Chidubem"),  
)
```

#### Set All
This is used to add multiple items to the database. If the operation is successfull return the added items. These will be added to the cache
```kotlin
controller.setAll(collection = usersCollection, User.serializer()) {  
  if (insertMany(users).wasAcknowledged())  
        users  
    else  
  emptyList()  
}
```
#### Get All
This is use to get all items from the database or from the cache if they already exist.
```kotlin
controller.getAll(usersCollection, User.serializer()) {  
  find().toList()  
}
```

You can provide a different cacheKey(something that describes the operation) if you are not returning all items

```kotlin
controller.getAll(usersCollection, User.serializer(), cacheKey = "all:firsName:Funyin") {  
  find(Filters.eq(User::firstName.name, "Funyin")).toList()  
}
```

#### Set
This will replace a single Item in the cache after execution
```kotlin
controller.set(usersCollection, User.serializer()) {  
  findOneAndUpdate(  
        Filters.eq("_id", users.first().id),  
        Updates.set(User::firstName.name, "NewNameFirst")  
    )  
}
```

#### Get
Retrieve a single item from the db or cache if it exists
```kotlin
controller.get(users.first().id, usersCollection, User.serializer()) {  
  find(Filters.eq(User::id.name, users.first().id)).firstOrNull()  
}
```

#### Remove
Remove a single item from the db or cache if it exists. If the operations returns `true` the items will be removed from the cache
```kotlin
controller.remove(users.first.id, usersCollection) {  
  deleteOne(Filters.eq("_id", users.first.id)).wasAcknowledged()  
}
```

### Remove All
Remove multiple items from the db or cache
```kotlin
controller.removeAll(usersCollection) {  
  deleteMany(Filters.or(users.map {  
					Filters.eq("_id", it.id)  
            })).wasAcknowledged()  
}
```

### Volatiles
Volatiles are operations whose result should change if the items in the collection arte updated.
A good example is pagination requests which will be cached on first request but should change when an item is added to the list.
**setAll** and **set** operations clear volatiles tied to a collection
```kotlin
val key = "${keyPrefix}page:$page|size:$size"  
val results = cacheController.getVolatile(  
    key,  
    collection,
) {  
  val skip = (page - 1) * size  
    val list = arrayListOf<T>()  
    list.addAll(getResults(skip).toList())  
    list  
}  
val countKey = "${keyPrefix}pagesCount|size:$size"  
val totalItems = cacheController.getVolatile(  
    countKey,  
    collection,
) {  
  collection.estimatedDocumentCount().awaitFirst()  
}
```



> **Tip:**
> 
> You will get log outputs for every operations just to let you know if the cache was used or not
> 
> `CACHE HIT getAll $cacheKey` -> Cache was used\
> `CACHE MISS getAll $cacheKey` -> Cache was not used (the data will be set after this)\
> `CACHE SET setAll $cacheKey - $response` -> Cache has been updated\
> `CACHE DROP remove $cacheKey - $response` -> Cache Item was removed
