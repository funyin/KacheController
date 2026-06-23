package com.funyinkash.kachecontroller.cache

import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.MapScanCursor
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisCacheClientTest {

    private val commands = mockk<RedisCoroutinesCommands<String, String>>()
    private lateinit var client: RedisCacheClient

    @BeforeEach
    fun setUp() {
        clearMocks(commands)
        client = RedisCacheClient("redis://localhost").apply {
            commandsProvider = { commands }
        }
    }

    private fun scanPage(
        entries: Map<String, String>,
        finished: Boolean = true,
    ): MapScanCursor<String, String> = mockk {
        every { map } returns entries
        every { isFinished } returns finished
    }

    @Test
    fun `hget delegates to commands`() = runTest {
        coEvery { commands.hget("k", "f") } returns "v"
        assertEquals("v", client.hget("k", "f"))
        coVerify { commands.hget("k", "f") }
    }

    @Test
    fun `hset delegates to commands with null safety`() = runTest {
        coEvery { commands.hset("k", "f", "v") } returns null
        assertFalse(client.hset("k", "f", "v"))
        coVerify { commands.hset("k", "f", "v") }
    }

    @Test
    fun `hset with map delegates to commands with null safety`() = runTest {
        coEvery { commands.hset("k", any<Map<String, String>>()) } returns null
        assertEquals(0L, client.hset("k", mapOf("a" to "1")))
    }

    @Test
    fun `hdel delegates to commands with null safety`() = runTest {
        coEvery { commands.hdel("k", "f") } returns null
        assertEquals(0L, client.hdel("k", "f"))
    }

    @Test
    fun `del delegates to commands with null safety`() = runTest {
        coEvery { commands.del("k") } returns null
        assertEquals(0L, client.del("k"))
    }

    @Test
    fun `exists returns true when count positive`() = runTest {
        coEvery { commands.exists("k") } returns 1L
        assertTrue(client.exists("k"))
    }

    @Test
    fun `exists returns false when count zero`() = runTest {
        coEvery { commands.exists("k") } returns 0L
        assertFalse(client.exists("k"))
    }

    @Test
    fun `exists returns false on null`() = runTest {
        coEvery { commands.exists("k") } returns null
        assertFalse(client.exists("k"))
    }

    @Test
    fun `hgetAll paginates through scan`() = runTest {
        val page1 = scanPage(mapOf("a" to "1"), finished = false)
        val page2 = scanPage(mapOf("b" to "2"), finished = true)

        coEvery { commands.hscan("k", ScanCursor.INITIAL, any<ScanArgs>()) } returns page1
        coEvery { commands.hscan("k", page1, any<ScanArgs>()) } returns page2

        val result = client.hgetAll("k")

        assertEquals(mapOf("a" to "1", "b" to "2"), result)
        coVerify(exactly = 1) { commands.hscan("k", ScanCursor.INITIAL, any()) }
        coVerify(exactly = 1) { commands.hscan("k", page1, any()) }
    }

    @Test
    fun `hgetAll handles single page`() = runTest {
        val page = scanPage(mapOf("a" to "1"), finished = true)
        coEvery { commands.hscan("k", ScanCursor.INITIAL, any<ScanArgs>()) } returns page

        assertEquals(mapOf("a" to "1"), client.hgetAll("k"))
        coVerify(exactly = 1) { commands.hscan("k", ScanCursor.INITIAL, any()) }
    }

    @Test
    fun `hgetAll handles null scan result`() = runTest {
        coEvery { commands.hscan("k", ScanCursor.INITIAL, any<ScanArgs>()) } returns null
        assertTrue(client.hgetAll("k").isEmpty())
    }

    @Test
    fun `expire converts kotlin duration to java duration`() = runTest {
        coEvery { commands.expire("k", any<java.time.Duration>()) } returns true
        client.expire("k", 5.seconds)
        coVerify { commands.expire("k", java.time.Duration.ofSeconds(5)) }
    }

    @Test
    fun `hexpire warns once and silences subsequent calls`() = runTest {
        coEvery { commands.hexpire("k", any<java.time.Duration>(), "f1", "f2") } throws
            RuntimeException("not supported")

        client.hexpire("k", 5.seconds, "f1", "f2")

        client.hexpire("k", 5.seconds, "f3") // should early-return

        coVerify(exactly = 1) { commands.hexpire("k", java.time.Duration.ofSeconds(5), "f1", "f2") }
        coVerify(exactly = 0) { commands.hexpire("k", java.time.Duration.ofSeconds(5), "f3") }
    }
}
