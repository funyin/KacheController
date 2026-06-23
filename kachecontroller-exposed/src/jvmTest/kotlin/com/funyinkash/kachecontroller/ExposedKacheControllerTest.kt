package com.funyinkash.kachecontroller

import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.serializer
import org.jetbrains.exposed.sql.Table
import org.junit.jupiter.api.Test
import kotlin.test.*

@Serializable
private data class User(override val id: String = "u1", val name: String = "Alice") : Model

private val USER_1 = User("u1", "Alice")
private val USER_2 = User("u2", "Bob")

object TestTable : Table("test_users") {
    val id = varchar("id", 36)
    val name = varchar("name", 100)
}

class ExposedKacheControllerTest {

    private val cache = mockk<CacheClient>(relaxed = true)
    private val controller = ExposedKacheController(cache = cache)

    @Test
    fun `get delegates with correct keys`() = runTest {
        coEvery { cache.hget("test_users", "u1") } returns null
        controller.get("u1", TestTable, User.serializer()) { null }

        coVerify { cache.hget("test_users", "u1") }
    }

    @Test
    fun `get cache miss fetches and stores`() = runTest {
        coEvery { cache.hget("test_users", USER_1.id) } returns null

        val result = controller.get(USER_1.id, TestTable, User.serializer()) { USER_1 }

        assertEquals(USER_1, result)
        coVerify { cache.hset("test_users", USER_1.id, any()) }
        coVerify { cache.del("test_users:volatile") }
    }

    @Test
    fun `get cache hit returns without DB call`() = runTest {
        val json = """{"id":"u1","name":"Alice"}"""
        coEvery { cache.hget("test_users", USER_1.id) } returns json

        val result = controller.get(USER_1.id, TestTable, User.serializer()) {
            fail("should not be called")
        }

        assertEquals(USER_1, result)
    }

    @Test
    fun `getAll delegates with correct keys`() = runTest {
        coEvery { cache.exists("test_users") } returns false

        val result = controller.getAll(TestTable, User.serializer()) { listOf(USER_1) }

        assertEquals(listOf(USER_1), result)
        coVerify { cache.hset("test_users", any<Map<String, String>>()) }
    }

    @Test
    fun `set clears volatiles`() = runTest {
        controller.set(TestTable, serializer = User.serializer()) { USER_1 }

        coVerify { cache.hset("test_users", USER_1.id, any()) }
        coVerify { cache.del("test_users:volatile") }
    }

    @Test
    fun `set with invalidateVolatiles false skips volatile clear`() = runTest {
        controller.set(TestTable, serializer = User.serializer(), invalidateVolatiles = false) { USER_1 }

        coVerify { cache.hset("test_users", USER_1.id, any()) }
        coVerify(exactly = 0) { cache.del("test_users:volatile") }
    }

    @Test
    fun `setAll caches and clears volatiles`() = runTest {
        controller.setAll(TestTable, User.serializer()) { listOf(USER_1, USER_2) }

        coVerify { cache.hset(eq("test_users"), any<Map<String, String>>()) }
        coVerify { cache.del("test_users:volatile") }
    }

    @Test
    fun `setAll null data returns false`() = runTest {
        val result = controller.setAll(TestTable, User.serializer()) { null }

        assertFalse(result)
        coVerify(exactly = 0) { cache.hset(any(), any<Map<String, String>>()) }
    }

    @Test
    fun `getVolatile cache hit returns cached value`() = runTest {
        coEvery { cache.hget("test_users:volatile", "count") } returns "42"

        val result = controller.getVolatile("count", TestTable, Long.serializer()) {
            fail("should not be called")
        }

        assertEquals(42L, result)
    }

    @Test
    fun `getVolatile cache miss fetches from DB`() = runTest {
        coEvery { cache.hget("test_users:volatile", "count") } returns null

        val result = controller.getVolatile("count", TestTable, Long.serializer()) { 99L }

        assertEquals(99L, result)
        coVerify { cache.hset("test_users:volatile", "count", "99") }
    }

    @Test
    fun `remove on success clears cache and volatiles`() = runTest {
        controller.remove(USER_1.id, TestTable) { true }

        coVerify { cache.hdel("test_users", USER_1.id) }
        coVerify { cache.del("test_users:volatile") }
    }

    @Test
    fun `remove on failure does not touch cache`() = runTest {
        controller.remove(USER_1.id, TestTable) { false }

        coVerify(exactly = 0) { cache.hdel(any(), any()) }
        coVerify(exactly = 0) { cache.del(any()) }
    }

    @Test
    fun `removeAll on success deletes entire key and volatiles`() = runTest {
        controller.removeAll(TestTable) { true }

        coVerify { cache.del("test_users") }
        coVerify { cache.del("test_users:volatile") }
    }

    @Test
    fun `cache disabled skips all cache operations`() = runTest {
        val disabled = ExposedKacheController(cacheEnabled = { false }, cache = cache)

        disabled.get(USER_1.id, TestTable, User.serializer()) { USER_1 }

        coVerify(exactly = 0) { cache.hget(any(), any()) }
        coVerify(exactly = 0) { cache.hset(any(), any<String>(), any()) }
    }
}
