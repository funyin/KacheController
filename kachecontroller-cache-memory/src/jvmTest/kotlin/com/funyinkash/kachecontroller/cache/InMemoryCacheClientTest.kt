package com.funyinkash.kachecontroller.cache

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.*

class InMemoryCacheClientTest {

    private fun cache() = InMemoryCacheClient()

    @Test
    fun `hget returns null for missing key`() = runTest {
        assertNull(cache().hget("missing", "f"))
    }

    @Test
    fun `hset and hget roundtrip single field`() = runTest {
        val c = cache()
        c.hset("k", "f", "v")
        assertEquals("v", c.hget("k", "f"))
    }

    @Test
    fun `hset returns true for new field`() = runTest {
        assertTrue(cache().hset("k", "f", "v"))
    }

    @Test
    fun `hset returns false when updating existing field`() = runTest {
        val c = cache()
        c.hset("k", "f", "v1")
        assertFalse(c.hset("k", "f", "v2"))
    }

    @Test
    fun `hset with map stores all entries`() = runTest {
        val c = cache()
        c.hset("k", mapOf("a" to "1", "b" to "2"))
        assertEquals("1", c.hget("k", "a"))
        assertEquals("2", c.hget("k", "b"))
    }

    @Test
    fun `hset with map returns count of new fields`() = runTest {
        val c = cache()
        c.hset("k", mapOf("a" to "1"))
        assertEquals(1L, c.hset("k", mapOf("a" to "x", "b" to "2")))
    }

    @Test
    fun `hdel returns count of removed fields`() = runTest {
        val c = cache()
        c.hset("k", "f", "v")
        assertEquals(1L, c.hdel("k", "f"))
        assertNull(c.hget("k", "f"))
    }

    @Test
    fun `hdel multiple fields`() = runTest {
        val c = cache()
        c.hset("k", mapOf("a" to "1", "b" to "2", "c" to "3"))
        assertEquals(2L, c.hdel("k", "a", "c"))
        assertNull(c.hget("k", "a"))
        assertEquals("2", c.hget("k", "b"))
        assertNull(c.hget("k", "c"))
    }

    @Test
    fun `hdel returns zero for missing field`() = runTest {
        assertEquals(0L, cache().hdel("k", "missing"))
    }

    @Test
    fun `del removes entire key`() = runTest {
        val c = cache()
        c.hset("k", mapOf("a" to "1", "b" to "2"))
        assertEquals(1L, c.del("k"))
        assertFalse(c.exists("k"))
    }

    @Test
    fun `del multiple keys`() = runTest {
        val c = cache()
        c.hset("k1", "f", "v")
        c.hset("k2", "f", "v")
        assertEquals(2L, c.del("k1", "k2"))
    }

    @Test
    fun `del returns zero for missing key`() = runTest {
        assertEquals(0L, cache().del("missing"))
    }

    @Test
    fun `exists returns true for existing key`() = runTest {
        val c = cache()
        c.hset("k", "f", "v")
        assertTrue(c.exists("k"))
    }

    @Test
    fun `exists returns false for missing key`() = runTest {
        assertFalse(cache().exists("missing"))
    }

    @Test
    fun `hgetAll returns all fields`() = runTest {
        val c = cache()
        c.hset("k", mapOf("a" to "1", "b" to "2"))
        assertEquals(mapOf("a" to "1", "b" to "2"), c.hgetAll("k"))
    }

    @Test
    fun `hgetAll returns empty map for missing key`() = runTest {
        assertTrue(cache().hgetAll("missing").isEmpty())
    }

    @Test
    fun `keys are isolated`() = runTest {
        val c = cache()
        c.hset("k1", "f", "v1")
        c.hset("k2", "f", "v2")
        assertEquals("v1", c.hget("k1", "f"))
        assertEquals("v2", c.hget("k2", "f"))
    }

    @Test
    fun `expire is safe to call`() = runTest {
        val c = cache()
        c.hset("k", "f", "v")
        c.expire("k", kotlin.time.Duration.INFINITE)
        assertEquals("v", c.hget("k", "f"))
    }

    @Test
    fun `hexpire is safe to call`() = runTest {
        val c = cache()
        c.hset("k", "f", "v")
        c.hexpire("k", kotlin.time.Duration.INFINITE, "f")
        assertEquals("v", c.hget("k", "f"))
    }
}
