package com.funyinkash.kachecontroller.cache

import com.funyinkash.kachecontroller.CacheClient
import io.lettuce.core.ExperimentalLettuceCoroutinesApi
import io.lettuce.core.RedisClient
import io.lettuce.core.ScanArgs
import io.lettuce.core.ScanCursor
import io.lettuce.core.api.coroutines.RedisCoroutinesCommands
import io.lettuce.core.api.coroutines
import org.slf4j.LoggerFactory
import kotlin.time.Duration as KotlinDuration

/**
 * Redis-backed [CacheClient] via the Lettuce coroutines API.
 *
 * The Redis connection is established lazily — the first command to execute
 * triggers [RedisClient.connect]. This means you can construct an instance
 * up front without requiring Redis to be running.
 *
 * Supports TTL via [expire] and per-field TTL via [hexpire] (Redis 7.4+).
 * When hexpire is not supported a one-time warning is logged and the call is silently ignored.
 *
 * @param uri  Redis connection URI (e.g. `redis://localhost:6379`).
 */
@OptIn(ExperimentalLettuceCoroutinesApi::class)
class RedisCacheClient(uri: String) : CacheClient {

    private val logger = LoggerFactory.getLogger("RedisCacheClient")

    /** Overridable commands provider — used internally for lazy connect, exposed for tests. */
    @PublishedApi internal var commandsProvider: () -> RedisCoroutinesCommands<String, String> = {
        RedisClient.create(uri).connect().coroutines()
    }

    private val commands by lazy { commandsProvider() }

    override suspend fun hget(key: String, field: String): String? = commands.hget(key, field)

    override suspend fun hset(key: String, field: String, value: String): Boolean =
        commands.hset(key, field, value) ?: false

    override suspend fun hset(key: String, entries: Map<String, String>): Long =
        commands.hset(key, entries) ?: 0L

    override suspend fun hdel(key: String, vararg fields: String): Long =
        commands.hdel(key, *fields) ?: 0L

    override suspend fun del(vararg keys: String): Long =
        commands.del(*keys) ?: 0L

    override suspend fun exists(key: String): Boolean =
        (commands.exists(key) ?: 0L) > 0L

    override suspend fun hgetAll(key: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val scanArgs = ScanArgs().apply { limit(100) }
        var cursor: ScanCursor = ScanCursor.INITIAL
        while (true) {
            val scanResult = commands.hscan(key, cursor, scanArgs) ?: break
            result.putAll(scanResult.map)
            cursor = scanResult
            if (scanResult.isFinished) break
        }
        return result
    }

    override suspend fun expire(key: String, ttl: KotlinDuration) {
        commands.expire(key, java.time.Duration.ofNanos(ttl.inWholeNanoseconds))
    }

    private var hexpireWarningLogged = false

    @OptIn(ExperimentalLettuceCoroutinesApi::class)
    override suspend fun hexpire(key: String, ttl: KotlinDuration, vararg fields: String) {
        if (hexpireWarningLogged) return
        try {
            commands.hexpire(key, java.time.Duration.ofNanos(ttl.inWholeNanoseconds), *fields)
        } catch (e: Exception) {
            if (!hexpireWarningLogged) {
                hexpireWarningLogged = true
                logger.warn("HEXPIRE not supported (Redis < 7.4 or Lettuce < 6.5), ignoring fieldExpire")
            }
        }
    }

}
