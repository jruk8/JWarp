# JWarp

Minimal Paper plugin with two commands and a tiny test/lint setup.

`/testwarp` teleports a player to the coordinates defined in `config.yml`.

`/jwarp reload` reloads both `config.yml` and `messages.yml`.

## Build

Use Gradle from the repository root:

- `./gradlew build` compiles the plugin, runs the tests, and packages the jar.
- `./gradlew check` runs the tests and the linter.

## Layout

- Source code goes in `src/main/java`.
- Runtime files go in `src/main/resources`.
- Tests go in `src/test/java`.
- Build output goes in `build/`.

## Notes

The project uses Gradle and Java 25.