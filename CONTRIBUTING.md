# Contributing

Keep changes small and focused.

## Working on the plugin

- Run `./gradlew check` before opening a PR or handing the repo off.
- Keep command behavior, messages, and permissions aligned.
- Update `README.md` when you change user-facing commands or config keys.
- The plugin merges new config/message keys on startup and reload, and applies
  small migrations when bundled defaults change shape.

## Build and release

- Build from the repository root with `./gradlew build`.
- Use `./gradlew check` for test and style verification.
- The project uses Gradle and Java 25.

## Modrinth publishing

The publish workflow is already wired for tag pushes, but the repo still needs
these settings in GitHub:

- `MODRINTH_TOKEN` as a GitHub secret
- `MODRINTH_ID` as a repository variable
- `MODRINTH_GAME_VERSIONS` as a repository variable

## Code style

- Follow the existing Java style in `src/main/java`.
- Prefer small helpers over large command methods.
- Keep tests targeted at pure logic where possible.

## Pull requests

- Describe what changed and why.
- Call out any new permissions, config keys, or breaking command changes.
