package com.funyinkash.kachecontroller.cache

import com.funyinkash.kachecontroller.CacheClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/** Thread-safe in-memory [CacheClient]. No external dependencies. */
class InMemoryCacheClient : CacheClient {

    private val store = mutableMapOf<String, MutableMap<String, String>>()
    private val lock = Mutex()

    override suspend fun hget(key: String, field: String): String? = lock.withLock {
        store[key]?.get(field)
    }

    override suspend fun hset(key: String, field: String, value: String): Boolean = lock.withLock {
        store.getOrPut(key) { mutableMapOf() }.put(field, value) == null
    }

    override suspend fun hset(key: String, entries: Map<String, String>): Long = lock.withLock {
        val hash = store.getOrPut(key) { mutableMapOf() }
        var count = 0L
        for ((k, v) in entries) {
            if (hash.put(k, v) == null) count++
        }
        count
    }

    override suspend fun hdel(key: String, vararg fields: String): Long = lock.withLock {
        store[key]?.let { hash ->
            fields.count { hash.remove(it) != null }.toLong()
        } ?: 0L
    }

    override suspend fun del(vararg keys: String): Long = lock.withLock {
        keys.count { store.remove(it) != null }.toLong()
    }

    override suspend fun exists(key: String): Boolean = lock.withLock {
        store.containsKey(key)
    }

    override suspend fun hgetAll(key: String): Map<String, String> = lock.withLock {
        store[key]?.toMap() ?: emptyMap()
    }

    override suspend fun expire(key: String, ttl: Duration) {}

    override suspend fun hexpire(key: String, ttl: Duration, vararg fields: String) {}
}
