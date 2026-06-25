# Contributing

## Getting started

1. Fork the repository on GitHub.
2. Clone your fork and open the project in IntelliJ IDEA.
3. Start local services (MongoDB on 27017, Redis on 6379):
   ```bash
   cd example
   docker-compose up -d
   ```
4. Run the example to verify your environment:
   ```bash
   ./gradlew :example:run
   ```

## Running tests

```bash
./gradlew test
```

Each cache module has a contract test (`CacheClientContractTest`) that all `CacheClient` implementations must pass.

## Adding a cache backend

1. Create a new module `kachecontroller-cache-<name>/`.
2. Implement `CacheClient` and extend `CacheClientContractTest`.
3. Add the module to `settings.gradle.kts`.
4. Open a pull request with a description of the backend and any infrastructure requirements.

## Pull request guidelines

- Keep each PR focused on one change.
- Include or update tests where relevant.
- Run `./gradlew build` before opening a PR.
- Reference any related issue in the PR description.
