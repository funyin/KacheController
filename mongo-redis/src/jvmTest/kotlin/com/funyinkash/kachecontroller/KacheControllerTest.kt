package com.funyinkash.kachecontroller

import com.mongodb.MongoNamespace
import com.mongodb.kotlin.client.coroutine.MongoCollection
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.MapScanCursor
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
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

// ── Fixture model ─────────────────────────────────────────────────────────────

@Serializable
private data class User(
    override val id: String = "u1",
    val name: String = "Alice",
) : Model

private val USER_1 = User("u1", "Alice")
private val USER_2 = User("u2", "Bob")

// ── Test suite ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class KacheControllerTest {

    private val redis = mockk<RedisCoroutinesCommands<String, String>>(relaxed = true)
    private val collection = mockk<MongoCollection<User>>()

    private val DB = "testDb"
    private val COL = "users"
    private val cacheKey = "$DB:$COL"
    private val volatileKey = "$cacheKey:volatile"

    private val controller = KacheController(client = redis)

    @BeforeEach
    fun setUp() {
        val namespace = mockk<MongoNamespace>()
        every { namespace.databaseName } returns DB
        every { namespace.collectionName } returns COL
        every { collection.namespace } returns namespace
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun scanPage(
        entries: Map<String, String>,
        finished: Boolean = true,
    ): MapScanCursor<String, String> = mockk {
        every { map } returns entries
        every { isFinished } returns finished
    }

    private fun userJson(u: User) = """{"id":"${u.id}","name":"${u.name}"}"""

    // ── get ───────────────────────────────────────────────────────────────────

    @Nested
    inner class Get {

        @Test
        fun `cache hit returns cached item without touching DB`() = runTest {
            coEvery { redis.hget(cacheKey, USER_1.id) } returns userJson(USER_1)

            val result = controller.get(USER_1.id, collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(USER_1, result)
            coVerify(exactly = 0) { redis.hset(any(), any<String>(), any()) }
        }

        @Test
        fun `cache miss fetches from DB and populates cache`() = runTest {
            coEvery { redis.hget(cacheKey, USER_1.id) } returns null

            val result = controller.get(USER_1.id, collection, User.serializer()) { USER_1 }

            assertEquals(USER_1, result)
            coVerify { redis.hset(cacheKey, USER_1.id, any()) }
        }

        @Test
        fun `cache miss with null DB result returns null without caching`() = runTest {
            coEvery { redis.hget(cacheKey, USER_1.id) } returns null

            val result = controller.get(USER_1.id, collection, User.serializer()) { null }

            assertNull(result)
            coVerify(exactly = 0) { redis.hset(any(), any<String>(), any()) }
        }

        @Test
        fun `cache disabled always calls DB and skips Redis`() = runTest {
            val disabled = KacheController(cacheEnabled = { false }, client = redis)

            val result = disabled.get(USER_1.id, collection, User.serializer()) { USER_1 }

            assertEquals(USER_1, result)
            coVerify(exactly = 0) { redis.hget(any(), any()) }
            coVerify(exactly = 0) { redis.hset(any(), any<String>(), any()) }
        }
    }

    // ── getAll ────────────────────────────────────────────────────────────────

    @Nested
    inner class GetAll {

        @Test
        fun `cache hit returns cached items without touching DB`() = runTest {
            val entries = mapOf(USER_1.id to userJson(USER_1), USER_2.id to userJson(USER_2))
            coEvery { redis.exists(cacheKey) } returns 1L
            coEvery { redis.hscan(cacheKey, ScanCursor.INITIAL, any()) } returns scanPage(entries)

            val result = controller.getAll(collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(2, result.size)
            assertTrue(result.containsAll(listOf(USER_1, USER_2)))
        }

        @Test
        fun `cache miss fetches from DB and populates cache`() = runTest {
            coEvery { redis.exists(cacheKey) } returns 0L

            val result = controller.getAll(collection, User.serializer()) { listOf(USER_1) }

            assertEquals(listOf(USER_1), result)
            coVerify { redis.hset(cacheKey, any<Map<String, String>>()) }
        }

        @Test
        fun `empty collection stores sentinel on miss`() = runTest {
            coEvery { redis.exists(cacheKey) } returns 0L

            val result = controller.getAll(collection, User.serializer()) { emptyList() }

            assertEquals(emptyList(), result)
            coVerify { redis.hset(cacheKey, "__kache_empty__", "1") }
        }

        @Test
        fun `sentinel is filtered out of returned results`() = runTest {
            coEvery { redis.exists(cacheKey) } returns 1L
            coEvery { redis.hscan(cacheKey, ScanCursor.INITIAL, any()) } returns
                scanPage(mapOf("__kache_empty__" to "1"))

            val result = controller.getAll(collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(emptyList(), result)
        }

        @Test
        fun `second call on empty collection is a cache hit`() = runTest {
            coEvery { redis.exists(cacheKey) } returnsMany listOf(0L, 1L)
            coEvery { redis.hscan(cacheKey, ScanCursor.INITIAL, any()) } returns
                scanPage(mapOf("__kache_empty__" to "1"))

            controller.getAll(collection, User.serializer()) { emptyList() }

            var dbCalls = 0
            controller.getAll(collection, User.serializer()) { dbCalls++; emptyList() }

            assertEquals(0, dbCalls)
        }

        // Regression: cursor was never advanced — caused an infinite loop on large hashes.
        @Test
        fun `multi-page hscan advances cursor correctly`() = runTest {
            val page1 = scanPage(mapOf(USER_1.id to userJson(USER_1)), finished = false)
            val page2 = scanPage(mapOf(USER_2.id to userJson(USER_2)), finished = true)

            coEvery { redis.exists(cacheKey) } returns 1L
            coEvery { redis.hscan(cacheKey, ScanCursor.INITIAL, any()) } returns page1
            coEvery { redis.hscan(cacheKey, page1, any()) } returns page2

            val result = controller.getAll(collection, User.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(2, result.size)
            coVerify(exactly = 1) { redis.hscan(cacheKey, ScanCursor.INITIAL, any()) }
            coVerify(exactly = 1) { redis.hscan(cacheKey, page1, any()) }
        }

        @Test
        fun `cache disabled always calls DB and skips Redis`() = runTest {
            val disabled = KacheController(cacheEnabled = { false }, client = redis)

            val result = disabled.getAll(collection, User.serializer()) { listOf(USER_1) }

            assertEquals(listOf(USER_1), result)
            coVerify(exactly = 0) { redis.exists(any()) }
            coVerify(exactly = 0) { redis.hscan(any(), any<ScanCursor>(), any()) }
        }
    }

    // ── set ───────────────────────────────────────────────────────────────────

    @Nested
    inner class Set {

        @Test
        fun `caches item and clears volatiles`() = runTest {
            val result = controller.set(collection, serializer = User.serializer()) { USER_1 }

            assertEquals(USER_1, result)
            coVerify { redis.hset(cacheKey, USER_1.id, any()) }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `null DB result skips cache and does not clear volatiles`() = runTest {
            val result = controller.set(collection, serializer = User.serializer()) { null }

            assertNull(result)
            coVerify(exactly = 0) { redis.hset(any(), any<String>(), any()) }
            coVerify(exactly = 0) { redis.del(volatileKey) }
        }

        @Test
        fun `cache disabled skips Redis entirely`() = runTest {
            val disabled = KacheController(cacheEnabled = { false }, client = redis)

            disabled.set(collection, serializer = User.serializer()) { USER_1 }

            coVerify(exactly = 0) { redis.hset(any(), any<String>(), any()) }
            coVerify(exactly = 0) { redis.del(any()) }
        }
    }

    // ── setAll ────────────────────────────────────────────────────────────────

    @Nested
    inner class SetAll {

        @Test
        fun `caches all items and clears volatiles`() = runTest {
            val result = controller.setAll(collection, User.serializer()) { listOf(USER_1, USER_2) }

            assertTrue(result)
            coVerify { redis.hset(cacheKey, any<Map<String, String>>()) }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `empty list stores sentinel and clears volatiles`() = runTest {
            val result = controller.setAll(collection, User.serializer()) { emptyList() }

            assertTrue(result)
            coVerify { redis.hset(cacheKey, "__kache_empty__", "1") }
            coVerify { redis.del(volatileKey) }
        }

        // Regression: force-unwrap !! on null returned from setData caused NPE.
        @Test
        fun `null DB result returns false without crashing`() = runTest {
            val result = controller.setAll(collection, User.serializer()) { null }

            assertFalse(result)
            coVerify(exactly = 0) { redis.hset(any(), any<Map<String, String>>()) }
            coVerify(exactly = 0) { redis.del(any()) }
        }
    }

    // ── remove ────────────────────────────────────────────────────────────────

    @Nested
    inner class Remove {

        // Regression: remove previously did not clear volatiles, leaving counts/pages stale.
        @Test
        fun `successful delete removes item from cache and clears volatiles`() = runTest {
            val result = controller.remove(USER_1.id, collection) { true }

            assertTrue(result)
            coVerify { redis.hdel(cacheKey, USER_1.id) }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `failed DB delete does not touch cache or volatiles`() = runTest {
            val result = controller.remove(USER_1.id, collection) { false }

            assertFalse(result)
            coVerify(exactly = 0) { redis.hdel(any(), any()) }
            coVerify(exactly = 0) { redis.del(any()) }
        }

        @Test
        fun `cache disabled skips Redis entirely`() = runTest {
            val disabled = KacheController(cacheEnabled = { false }, client = redis)

            disabled.remove(USER_1.id, collection) { true }

            coVerify(exactly = 0) { redis.hdel(any(), any()) }
            coVerify(exactly = 0) { redis.del(any()) }
        }
    }

    // ── removeAll ─────────────────────────────────────────────────────────────

    @Nested
    inner class RemoveAll {

        // Regression: previously used HKEYS + HDEL (two round-trips, race window).
        @Test
        fun `successful delete uses atomic DEL not HKEYS plus HDEL`() = runTest {
            val result = controller.removeAll(collection) { true }

            assertTrue(result)
            coVerify { redis.del(cacheKey) }
            coVerify(exactly = 0) { redis.hkeys(any()) }
            coVerify(exactly = 0) { redis.hdel(any(), any()) }
        }

        // Regression: removeAll previously did not clear volatiles.
        @Test
        fun `successful delete clears volatiles`() = runTest {
            controller.removeAll(collection) { true }

            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `failed DB delete does not touch cache or volatiles`() = runTest {
            val result = controller.removeAll(collection) { false }

            assertFalse(result)
            coVerify(exactly = 0) { redis.del(any()) }
        }

        @Test
        fun `cache disabled skips Redis entirely`() = runTest {
            val disabled = KacheController(cacheEnabled = { false }, client = redis)

            disabled.removeAll(collection) { true }

            coVerify(exactly = 0) { redis.del(any()) }
        }
    }

    // ── getVolatile ───────────────────────────────────────────────────────────

    @Nested
    inner class GetVolatile {

        @Test
        fun `cache hit returns cached value without calling DB`() = runTest {
            coEvery { redis.hget(volatileKey, "count") } returns "42"

            val result = controller.getVolatile("count", collection, Long.serializer()) {
                fail("DB must not be called on a cache hit")
            }

            assertEquals(42L, result)
        }

        @Test
        fun `cache miss fetches from DB and stores in volatile hash`() = runTest {
            coEvery { redis.hget(volatileKey, "count") } returns null

            val result = controller.getVolatile("count", collection, Long.serializer()) { 99L }

            assertEquals(99L, result)
            coVerify { redis.hset(volatileKey, "count", any()) }
        }

        @Test
        fun `cache disabled always calls DB and skips Redis`() = runTest {
            val disabled = KacheController(cacheEnabled = { false }, client = redis)

            val result = disabled.getVolatile("count", collection, Long.serializer()) { 7L }

            assertEquals(7L, result)
            coVerify(exactly = 0) { redis.hget(any(), any()) }
        }
    }

    // ── volatile invalidation matrix ──────────────────────────────────────────

    @Nested
    inner class VolatileInvalidation {

        @Test
        fun `set clears volatiles`() = runTest {
            controller.set(collection, serializer = User.serializer()) { USER_1 }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `setAll clears volatiles`() = runTest {
            controller.setAll(collection, User.serializer()) { listOf(USER_1) }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `remove clears volatiles on success`() = runTest {
            controller.remove(USER_1.id, collection) { true }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `remove does not clear volatiles on failure`() = runTest {
            controller.remove(USER_1.id, collection) { false }
            coVerify(exactly = 0) { redis.del(volatileKey) }
        }

        @Test
        fun `removeAll clears volatiles on success`() = runTest {
            controller.removeAll(collection) { true }
            coVerify { redis.del(volatileKey) }
        }

        @Test
        fun `removeAll does not clear volatiles on failure`() = runTest {
            controller.removeAll(collection) { false }
            coVerify(exactly = 0) { redis.del(volatileKey) }
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
            val scoped = KacheController(client = redis, asyncWriteScope = scope)

            scoped.setAsync(USER_1, collection, User.serializer()) { }

            coVerify { redis.hset(cacheKey, USER_1.id, any()) }
            coVerify { redis.del(volatileKey) }
            scope.cancel()
        }

        @Test
        fun `setAllAsync with scope writes to cache immediately`() = runTest {
            val scope = CoroutineScope(coroutineContext + Job())
            val scoped = KacheController(client = redis, asyncWriteScope = scope)

            scoped.setAllAsync(listOf(USER_1, USER_2), collection, User.serializer()) { }

            coVerify { redis.hset(cacheKey, any<Map<String, String>>()) }
            coVerify { redis.del(volatileKey) }
            scope.cancel()
        }
    }
}
