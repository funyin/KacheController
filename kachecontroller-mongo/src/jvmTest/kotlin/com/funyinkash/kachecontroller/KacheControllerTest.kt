package com.funyinkash.kachecontroller

import com.mongodb.MongoNamespace
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.*

@Serializable
private data class User(
    override val id: String = "u1",
    val name: String = "Alice",
) : Model

private val USER_1 = User("u1", "Alice")
private val USER_2 = User("u2", "Bob")

class KacheControllerTest {

    private val cache = mockk<CacheClient>(relaxed = true)
    private val collection = mockk<MongoCollection<User>>()

    private val DB = "testDb"
    private val COL = "users"
    private val cacheKey = "$DB:$COL"
    private val volatileKey = "$cacheKey:volatile"

    private val controller = MongoKacheController(cache = cache)

    @BeforeEach
    fun setUp() {
        val namespace = mockk<MongoNamespace>()
        every { namespace.databaseName } returns DB
        every { namespace.collectionName } returns COL
        every { collection.namespace } returns namespace
    }

    private fun userJson(u: User) = """{"id":"${u.id}","name":"${u.name}"}"""

    // ── get ───────────────────────────────────────────────────────────────────

    @Nested
    inner class Get {

        @Test
        fun `cache hit returns cached item without touching DB`() = runTest {
            coEvery { cache.hget(cacheKey, USER_1.id) } returns userJson(USER_1)

            val result = controller.get(USER_1.id, collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(USER_1, result)
            coVerify(exactly = 0) { cache.hset(cacheKey, USER_1.id, any()) }
        }

        @Test
        fun `cache miss fetches from DB and populates cache`() = runTest {
            coEvery { cache.hget(cacheKey, USER_1.id) } returns null

            val result = controller.get(USER_1.id, collection, User.serializer()) { USER_1 }

            assertEquals(USER_1, result)
            coVerify { cache.hset(cacheKey, USER_1.id, any()) }
        }

        @Test
        fun `cache miss with null DB result returns null without caching`() = runTest {
            coEvery { cache.hget(cacheKey, USER_1.id) } returns null

            val result = controller.get(USER_1.id, collection, User.serializer()) { null }

            assertNull(result)
            coVerify(exactly = 0) { cache.hset(cacheKey, USER_1.id, any()) }
        }

        @Test
        fun `cache disabled always calls DB and skips cache`() = runTest {
            val disabled = MongoKacheController(cacheEnabled = { false }, cache = cache)

            val result = disabled.get(USER_1.id, collection, User.serializer()) { USER_1 }

            assertEquals(USER_1, result)
            coVerify(exactly = 0) { cache.hget(any(), any()) }
            coVerify(exactly = 0) { cache.hset(any(), any<String>(), any()) }
        }
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Nested
    inner class GetAll {

        @Test
        fun `cache hit returns cached items without touching DB`() = runTest {
            val entries = mapOf(USER_1.id to userJson(USER_1), USER_2.id to userJson(USER_2))
            coEvery { cache.exists(cacheKey) } returns true
            coEvery { cache.hgetAll(cacheKey) } returns entries

            val result = controller.getAll(collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf(USER_1, USER_2)))
        }

        @Test
        fun `cache miss fetches from DB and populates cache`() = runTest {
            coEvery { cache.exists(cacheKey) } returns false

            val result = controller.getAll(collection, User.serializer()) { listOf(USER_1) }

            assertEquals(listOf(USER_1), result)
            coVerify { cache.hset(cacheKey, any<Map<String, String>>()) }
        }

        @Test
        fun `empty collection stores sentinel on miss`() = runTest {
            coEvery { cache.exists(cacheKey) } returns false

            val result = controller.getAll(collection, User.serializer()) { emptyList() }

            assertEquals(emptyList(), result)
            coVerify { cache.hset(cacheKey, "__kache_empty__", "1") }
        }

        @Test
        fun `sentinel is filtered out of returned results`() = runTest {
            coEvery { cache.exists(cacheKey) } returns true
            coEvery { cache.hgetAll(cacheKey) } returns mapOf("__kache_empty__" to "1")

            val result = controller.getAll(collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(emptyList(), result)
        }

        @Test
        fun `second call on empty collection is a cache hit`() = runTest {
            coEvery { cache.exists(cacheKey) } returnsMany listOf(false, true)
            coEvery { cache.hgetAll(cacheKey) } returns mapOf("__kache_empty__" to "1")

            controller.getAll(collection, User.serializer()) { emptyList() }

            var dbCalls = 0
            controller.getAll(collection, User.serializer()) { dbCalls++; emptyList() }

            assertEquals(0, dbCalls)
        }

        @Test
        fun `cache disabled always calls DB and skips cache`() = runTest {
            val disabled = MongoKacheController(cacheEnabled = { false }, cache = cache)

            val result = disabled.getAll(collection, User.serializer()) { listOf(USER_1) }

            assertEquals(listOf(USER_1), result)
            coVerify(exactly = 0) { cache.exists(any()) }
            coVerify(exactly = 0) { cache.hgetAll(any()) }
        }
    }

    // ── set ───────────────────────────────────────────────────────────────────

    @Nested
    inner class Set {

        @Test
        fun `caches item and clears volatiles`() = runTest {
            val result = controller.set(collection, serializer = User.serializer()) { USER_1 }

            assertEquals(USER_1, result)
            coVerify { cache.hset(cacheKey, USER_1.id, any()) }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `null DB result skips cache and does not clear volatiles`() = runTest {
            val result = controller.set(collection, serializer = User.serializer()) { null }

            assertNull(result)
            coVerify(exactly = 0) { cache.hset(any(), any<String>(), any()) }
            coVerify(exactly = 0) { cache.del(volatileKey) }
        }

        @Test
        fun `cache disabled skips cache entirely`() = runTest {
            val disabled = MongoKacheController(cacheEnabled = { false }, cache = cache)

            disabled.set(collection, serializer = User.serializer()) { USER_1 }

            coVerify(exactly = 0) { cache.hset(any(), any<String>(), any()) }
            coVerify(exactly = 0) { cache.del(any()) }
        }
    }

    // ── setAll ────────────────────────────────────────────────────────────────

    @Nested
    inner class SetAll {

        @Test
        fun `caches all items and clears volatiles`() = runTest {
            val result = controller.setAll(collection, User.serializer()) { listOf(USER_1, USER_2) }

            assertTrue(result)
            coVerify { cache.hset(cacheKey, any<Map<String, String>>()) }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `empty list stores sentinel and clears volatiles`() = runTest {
            val result = controller.setAll(collection, User.serializer()) { emptyList() }

            assertTrue(result)
            coVerify { cache.hset(cacheKey, "__kache_empty__", "1") }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `null DB result returns false without crashing`() = runTest {
            val result = controller.setAll(collection, User.serializer()) { null }

            assertFalse(result)
            coVerify(exactly = 0) { cache.hset(any(), any<Map<String, String>>()) }
            coVerify(exactly = 0) { cache.del(any()) }
        }
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Nested
    inner class Remove {

        @Test
        fun `successful delete removes item from cache and clears volatiles`() = runTest {
            val result = controller.remove(USER_1.id, collection) { true }

            assertTrue(result)
            coVerify { cache.hdel(cacheKey, USER_1.id) }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `failed DB delete does not touch cache or volatiles`() = runTest {
            val result = controller.remove(USER_1.id, collection) { false }

            assertFalse(result)
            coVerify(exactly = 0) { cache.hdel(any(), any()) }
            coVerify(exactly = 0) { cache.del(any()) }
        }

        @Test
        fun `cache disabled skips cache entirely`() = runTest {
            val disabled = MongoKacheController(cacheEnabled = { false }, cache = cache)

            disabled.remove(USER_1.id, collection) { true }

            coVerify(exactly = 0) { cache.hdel(any(), any()) }
            coVerify(exactly = 0) { cache.del(any()) }
        }
    }

    // ── removeAll ─────────────────────────────────────────────────────────────

    @Nested
    inner class RemoveAll {

        @Test
        fun `successful delete uses atomic DEL`() = runTest {
            val result = controller.removeAll(collection) { true }

            assertTrue(result)
            coVerify { cache.del(cacheKey) }
        }

        @Test
        fun `successful delete clears volatiles`() = runTest {
            controller.removeAll(collection) { true }

            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `failed DB delete does not touch cache or volatiles`() = runTest {
            val result = controller.removeAll(collection) { false }

            assertFalse(result)
            coVerify(exactly = 0) { cache.del(any()) }
        }

        @Test
        fun `cache disabled skips cache entirely`() = runTest {
            val disabled = MongoKacheController(cacheEnabled = { false }, cache = cache)

            disabled.removeAll(collection) { true }

            coVerify(exactly = 0) { cache.del(any()) }
        }
    }

    // ── getVolatile ───────────────────────────────────────────────────────────

    @Nested
    inner class GetVolatile {

        @Test
        fun `cache hit returns cached value without calling DB`() = runTest {
            coEvery { cache.hget(volatileKey, "count") } returns "42"

            val result = controller.getVolatile("count", collection, Long.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(42L, result)
        }

        @Test
        fun `cache miss fetches from DB and stores in volatile hash`() = runTest {
            coEvery { cache.hget(volatileKey, "count") } returns null

            val result = controller.getVolatile("count", collection, Long.serializer()) { 99L }

            assertEquals(99L, result)
            coVerify { cache.hset(volatileKey, "count", any()) }
        }

        @Test
        fun `cache disabled always calls DB and skips cache`() = runTest {
            val disabled = MongoKacheController(cacheEnabled = { false }, cache = cache)

            val result = disabled.getVolatile("count", collection, Long.serializer()) { 7L }

            assertEquals(7L, result)
            coVerify(exactly = 0) { cache.hget(any(), any()) }
        }
    }

    // ── volatile invalidation matrix ──────────────────────────────────────────

    @Nested
    inner class VolatileInvalidation {

        @Test
        fun `set clears volatiles`() = runTest {
            controller.set(collection, serializer = User.serializer()) { USER_1 }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `setAll clears volatiles`() = runTest {
            controller.setAll(collection, User.serializer()) { listOf(USER_1) }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `remove clears volatiles on success`() = runTest {
            controller.remove(USER_1.id, collection) { true }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `remove does not clear volatiles on failure`() = runTest {
            controller.remove(USER_1.id, collection) { false }
            coVerify(exactly = 0) { cache.del(volatileKey) }
        }

        @Test
        fun `removeAll clears volatiles on success`() = runTest {
            controller.removeAll(collection) { true }
            coVerify { cache.del(volatileKey) }
        }

        @Test
        fun `removeAll does not clear volatiles on failure`() = runTest {
            controller.removeAll(collection) { false }
            coVerify(exactly = 0) { cache.del(volatileKey) }
        }
    }

    // ── async write-behind ────────────────────────────────────────────────────

    @Nested
    inner class AsyncWrites {

        @Test
        fun `setAsync without scope throws immediately`() = runTest {
            try {
                controller.setAsync(USER_1, collection, User.serializer()) { }
                fail("Expected IllegalStateException")
            } catch (_: IllegalStateException) { }
        }

        @Test
        fun `setAllAsync without scope throws immediately`() = runTest {
            try {
                controller.setAllAsync(listOf(USER_1), collection, User.serializer()) { }
                fail("Expected IllegalStateException")
            } catch (_: IllegalStateException) { }
        }

        @Test
        fun `setAsync with scope writes to cache immediately`() = runTest {
            val scope = CoroutineScope(coroutineContext + Job())
            val scoped = MongoKacheController(cache = cache, asyncWriteScope = scope)

            scoped.setAsync(USER_1, collection, User.serializer()) { }

            coVerify { cache.hset(cacheKey, USER_1.id, any()) }
            coVerify { cache.del(volatileKey) }
            scope.cancel()
        }

        @Test
        fun `setAllAsync with scope writes to cache immediately`() = runTest {
            val scope = CoroutineScope(coroutineContext + Job())
            val scoped = MongoKacheController(cache = cache, asyncWriteScope = scope)

            scoped.setAllAsync(listOf(USER_1, USER_2), collection, User.serializer()) { }

            coVerify { cache.hset(cacheKey, any<Map<String, String>>()) }
            coVerify { cache.del(volatileKey) }
            scope.cancel()
        }
    }
}
