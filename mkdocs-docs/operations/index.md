# Operations

KacheController exposes six operation families. Each wraps a database lambda and handles the cache transparently.

| Operation | Cache on hit | Cache on miss | Clears volatiles |
|---|---|---|---|
| `get` | Returns cached value | Fetches from DB, stores result | No |
| `getAll` | Returns cached list | Fetches from DB, stores result | No |
| `getVolatile` | Returns cached value | Fetches from DB, stores result | No |
| `set` | — | — | Yes (default) |
| `setAll` | — | — | Yes (default) |
| `remove` | — | Evicts the field | Yes |
| `removeAll` | — | Deletes the hash | Yes |
| `setAsync` / `setAllAsync` | Updates cache immediately | — | Yes |

Read operations are described in [Read](read.md), write operations in [Write](write.md), and deletes in [Delete](delete.md).
Write-behind variants are covered in [Write-Behind](write_behind.md).
