package com.funyinkash.kachecontroller

import kotlin.time.Duration

/** Swappable backend for hash-based cache operations. */
interface CacheClient {
    /** Get a single field value. Returns `null` if the key or field does not exist. */
    suspend fun hget(key: String, field: String): String?

    /** Set a field value. Returns `true` if a new field was added, `false` if updated. */
    suspend fun hset(key: String, field: String, value: String): Boolean

    /** Set multiple fields. Returns the number of newly-added fields. */
    suspend fun hset(key: String, entries: Map<String, String>): Long

    /** Remove one or more fields. Returns the number of removed fields. */
    suspend fun hdel(key: String, vararg fields: String): Long

    /** Remove one or more whole hashes. Returns the number of removed keys. */
    suspend fun del(vararg keys: String): Long

    /** Returns `true` if the key exists (has at least one field). */
    suspend fun exists(key: String): Boolean

    /** Return every field-value pair in the hash. */
    suspend fun hgetAll(key: String): Map<String, String>

    /** Optional: set TTL on the whole hash. No-op by default. */
    suspend fun expire(key: String, ttl: Duration) {}

    /** Optional: set per-field TTL (Redis 7.4+). No-op by default. */
    suspend fun hexpire(key: String, ttl: Duration, vararg fields: String) {}
}
