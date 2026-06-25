# Installation

Pick one **cache backend** and one **database adapter**. `kachecontroller-core` is pulled in transitively.

!!! tip "Latest version"
    Check the [releases page](https://github.com/funyin/KacheController/releases) for the current version.
    Replace `KACHECONTROLLER_VERSION` in the snippets below.

## Artifacts

| Module | Artifact ID | What it provides |
|---|---|---|
| Core | `kachecontroller-core` | `Model`, `CacheClient`, `KacheController` |
| Redis cache | `kachecontroller-cache-redis` | `RedisCacheClient` (Lettuce) |
| In-memory cache | `kachecontroller-cache-memory` | `InMemoryCacheClient` (no external deps) |
| SQLite cache | `kachecontroller-cache-sqlite` | `SQLiteCacheClient` (sqlite-jdbc) |
| MongoDB adapter | `kachecontroller-mongo` | `MongoKacheController` |
| Exposed adapter | `kachecontroller-exposed` | `ExposedKacheController` |

## Adding dependencies

=== "Kotlin Gradle"

    ```kotlin
    dependencies {
        // pick one cache backend
        implementation("com.funyinkash:kachecontroller-cache-redis:KACHECONTROLLER_VERSION")
        // or:
        // implementation("com.funyinkash:kachecontroller-cache-memory:KACHECONTROLLER_VERSION")
        // implementation("com.funyinkash:kachecontroller-cache-sqlite:KACHECONTROLLER_VERSION")

        // pick one database adapter
        implementation("com.funyinkash:kachecontroller-mongo:KACHECONTROLLER_VERSION")
        // or:
        // implementation("com.funyinkash:kachecontroller-exposed:KACHECONTROLLER_VERSION")
    }
    ```

=== "Groovy Gradle"

    ```groovy
    dependencies {
        // pick one cache backend
        implementation 'com.funyinkash:kachecontroller-cache-redis:KACHECONTROLLER_VERSION'
        // or:
        // implementation 'com.funyinkash:kachecontroller-cache-memory:KACHECONTROLLER_VERSION'
        // implementation 'com.funyinkash:kachecontroller-cache-sqlite:KACHECONTROLLER_VERSION'

        // pick one database adapter
        implementation 'com.funyinkash:kachecontroller-mongo:KACHECONTROLLER_VERSION'
        // or:
        // implementation 'com.funyinkash:kachecontroller-exposed:KACHECONTROLLER_VERSION'
    }
    ```

=== "Maven"

    ```xml
    <!-- pick one cache backend -->
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kachecontroller-cache-redis-jvm</artifactId>
        <version>KACHECONTROLLER_VERSION</version>
    </dependency>

    <!-- pick one database adapter -->
    <dependency>
        <groupId>com.funyinkash</groupId>
        <artifactId>kachecontroller-mongo-jvm</artifactId>
        <version>KACHECONTROLLER_VERSION</version>
    </dependency>
    ```

## Serialization

All models must use `kotlinx-serialization`. Add the plugin if it is not already present:

=== "Kotlin Gradle"

    ```kotlin
    plugins {
        kotlin("plugin.serialization") version "1.9.0"
    }
    ```

=== "Groovy Gradle"

    ```groovy
    plugins {
        id 'org.jetbrains.kotlin.plugin.serialization' version '1.9.0'
    }
    ```
