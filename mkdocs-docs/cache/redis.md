# Redis Cache

`RedisCacheClient` wraps [Lettuce](https://lettuce.io/) coroutines commands and provides full `CacheClient` support including per-field TTL (requires Redis 7.4+).

## Setup

```kotlin
val cache: CacheClient = RedisCacheClient("redis://localhost:6379")
```

The constructor accepts any Lettuce URI string, including:

```
redis://[:password@]host[:port][/database]
rediss://host:port          # TLS
redis-sentinel://...
```

## Per-field TTL

`RedisCacheClient` implements `hexpire` using `HEXPIRE` (Redis 7.4+). When you pass `fieldExpire` to a controller method, it calls `HEXPIRE` after each `HSET`.

On Redis < 7.4 the command fails on the first call; the client logs a one-time warning and degrades gracefully — cached data is served without a TTL.

```kotlin
controller.set(users, User.serializer(), fieldExpire = Duration.ofHours(1)) {
    findOneAndUpdate(filter, update)
}
```

## Whole-hash TTL

`expire` maps to `EXPIRE` and applies to the entire hash key:

```kotlin
controller.getAll(users, User.serializer(), expire = Duration.ofMinutes(10)) {
    find().toList()
}
```

!!! warning
    `expire` and `fieldExpire` are independent. Setting `expire` on the collection hash evicts all documents at once. Use `fieldExpire` when you want document-level expiry; use `expire` when you want the entire collection cache to refresh on a schedule.
