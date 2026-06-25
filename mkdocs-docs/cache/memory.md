# In-Memory Cache

`InMemoryCacheClient` stores data in a `ConcurrentHashMap`. No external dependencies, no configuration.

## Setup

```kotlin
val cache: CacheClient = InMemoryCacheClient()
```

## Characteristics

- **No persistence** — cache is lost when the process stops.
- **No TTL support** — `expire` and `hexpire` are no-ops; data lives until explicitly evicted.
- **Not shared** — each process has its own independent cache. Do not use in multi-instance deployments where cache consistency matters.
- **Ideal for tests** — deterministic, fast, zero infrastructure.

## Testing

`InMemoryCacheClient` is the recommended backend for unit and integration tests:

```kotlin
@BeforeEach
fun setUp() {
    cache = InMemoryCacheClient()
    controller = MongoKacheController(cache = cache)
}
```

Starting each test with a fresh instance ensures no state leaks between tests.
