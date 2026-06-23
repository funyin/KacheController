package com.funyinkash.kachecontroller.cache

import com.funyinkash.kachecontroller.CacheClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.sqlite.SQLiteDataSource
import javax.sql.DataSource
import kotlin.time.Duration

/** Persistent [CacheClient] backed by a SQLite table via [javax.sql.DataSource]. */
class SQLiteCacheClient(private val dataSource: DataSource) : CacheClient {

    init {
        createTable()
    }

    private fun createTable() {
        dataSource.connection.use { conn ->
            conn.createStatement().execute(
                """
                CREATE TABLE IF NOT EXISTS kache (
                    hash_key TEXT NOT NULL,
                    field    TEXT NOT NULL,
                    value    TEXT NOT NULL,
                    PRIMARY KEY (hash_key, field)
                )
                """.trimIndent()
            )
        }
    }

    override suspend fun hget(key: String, field: String): String? = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT value FROM kache WHERE hash_key=? AND field=?").use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, field)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString("value") else null
                }
            }
        }
    }

    override suspend fun hset(key: String, field: String, value: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT OR REPLACE INTO kache (hash_key, field, value) VALUES (?, ?, ?)").use { stmt ->
                stmt.setString(1, key)
                stmt.setString(2, field)
                stmt.setString(3, value)
                stmt.executeUpdate()
            }
        }
        true
    }

    override suspend fun hset(key: String, entries: Map<String, String>): Long = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("INSERT OR REPLACE INTO kache (hash_key, field, value) VALUES (?, ?, ?)").use { stmt ->
                var count = 0L
                for ((field, value) in entries) {
                    stmt.setString(1, key)
                    stmt.setString(2, field)
                    stmt.setString(3, value)
                    stmt.addBatch()
                    count++
                }
                stmt.executeBatch()
                count
            }
        }
    }

    override suspend fun hdel(key: String, vararg fields: String): Long = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val placeholders = fields.joinToString(",") { "?" }
            conn.prepareStatement("DELETE FROM kache WHERE hash_key=? AND field IN ($placeholders)").use { stmt ->
                stmt.setString(1, key)
                fields.forEachIndexed { i, f -> stmt.setString(i + 2, f) }
                stmt.executeUpdate().toLong()
            }
        }
    }

    override suspend fun del(vararg keys: String): Long = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            val placeholders = keys.joinToString(",") { "?" }
            conn.prepareStatement("DELETE FROM kache WHERE hash_key IN ($placeholders)").use { stmt ->
                keys.forEachIndexed { i, k -> stmt.setString(i + 1, k) }
                stmt.executeUpdate().toLong()
            }
        }
    }

    override suspend fun exists(key: String): Boolean = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT COUNT(*) FROM kache WHERE hash_key=?").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    rs.next() && rs.getLong(1) > 0
                }
            }
        }
    }

    override suspend fun hgetAll(key: String): Map<String, String> = withContext(Dispatchers.IO) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("SELECT field, value FROM kache WHERE hash_key=?").use { stmt ->
                stmt.setString(1, key)
                stmt.executeQuery().use { rs ->
                    val result = mutableMapOf<String, String>()
                    while (rs.next()) {
                        result[rs.getString("field")] = rs.getString("value")
                    }
                    result
                }
            }
        }
    }

    override suspend fun expire(key: String, ttl: Duration) {}

    override suspend fun hexpire(key: String, ttl: Duration, vararg fields: String) {}

    companion object {
        /** Create an instance backed by the given JDBC URL. Defaults to a file-local database. */
        fun create(jdbcUrl: String = "jdbc:sqlite:kache.db"): SQLiteCacheClient {
            val ds = SQLiteDataSource().apply { url = jdbcUrl }
            return SQLiteCacheClient(ds)
        }
    }
}
