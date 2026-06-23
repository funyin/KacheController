package com.funyinkash.kachecontroller.cache

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.*
import kotlin.time.Duration
import java.io.File

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SQLiteCacheClientTest {

    companion object {
        private const val DB_URL = "jdbc:sqlite:test_kache.db"
    }

    private val cache = SQLiteCacheClient.create(DB_URL)

    @AfterAll
    fun cleanUpFile() {
        File("test_kache.db").delete()
    }

    @BeforeEach
    fun setUp() = runTest {
        cache.del("k", "k1", "k2", "k3", "missing")
    }

    @Test
    fun `hget returns null for missing key`() = runTest {
        assertNull(cache.hget("missing", "f"))
    }

    @Test
    fun `hset and hget roundtrip single field`() = runTest {
        cache.hset("k", "f", "v")
        assertEquals("v", cache.hget("k", "f"))
    }

    @Test
    fun `hset returns true for new field`() = runTest {
        assertTrue(cache.hset("k", "f", "v"))
    }

    @Test
    fun `hset returns true when updating existing field`() = runTest {
        cache.hset("k", "f", "v1")
        assertTrue(cache.hset("k", "f", "v2"))
    }

    @Test
    fun `hset with map stores all entries`() = runTest {
        cache.hset("k", mapOf("a" to "1", "b" to "2"))
        assertEquals("1", cache.hget("k", "a"))
        assertEquals("2", cache.hget("k", "b"))
    }

    @Test
    fun `hset with map returns count of entries`() = runTest {
        assertEquals(2L, cache.hset("k", mapOf("a" to "1", "b" to "2")))
    }

    @Test
    fun `hdel returns count of removed fields`() = runTest {
        cache.hset("k", "f", "v")
        assertEquals(1L, cache.hdel("k", "f"))
        assertNull(cache.hget("k", "f"))
    }

    @Test
    fun `hdel returns zero for missing field`() = runTest {
        assertEquals(0L, cache.hdel("k", "missing"))
    }

    @Test
    fun `del removes entire key`() = runTest {
        cache.hset("k", mapOf("a" to "1", "b" to "2"))
        assertTrue(cache.del("k") >= 1L)
        assertFalse(cache.exists("k"))
    }

    @Test
    fun `del multiple keys`() = runTest {
        cache.hset("k1", "f", "v")
        cache.hset("k2", "f", "v")
        assertEquals(2L, cache.del("k1", "k2"))
    }

    @Test
    fun `del returns zero for missing key`() = runTest {
        assertEquals(0L, cache.del("missing"))
    }

    @Test
    fun `exists returns true for existing key`() = runTest {
        cache.hset("k", "f", "v")
        assertTrue(cache.exists("k"))
    }

    @Test
    fun `exists returns false for missing key`() = runTest {
        assertFalse(cache.exists("missing"))
    }

    @Test
    fun `hgetAll returns all fields`() = runTest {
        cache.hset("k", mapOf("a" to "1", "b" to "2"))
        assertEquals(mapOf("a" to "1", "b" to "2"), cache.hgetAll("k"))
    }

    @Test
    fun `hgetAll returns empty map for missing key`() = runTest {
        assertTrue(cache.hgetAll("missing").isEmpty())
    }

    @Test
    fun `keys are isolated`() = runTest {
        cache.hset("k1", "f", "v1")
        cache.hset("k2", "f", "v2")
        assertEquals("v1", cache.hget("k1", "f"))
        assertEquals("v2", cache.hget("k2", "f"))
    }

    @Test
    fun `expire is safe to call`() = runTest {
        cache.hset("k", "f", "v")
        cache.expire("k", Duration.INFINITE)
        assertEquals("v", cache.hget("k", "f"))
    }

    @Test
    fun `hexpire is safe to call`() = runTest {
        cache.hset("k", "f", "v")
        cache.hexpire("k", Duration.INFINITE, "f")
        assertEquals("v", cache.hget("k", "f"))
    }
}
