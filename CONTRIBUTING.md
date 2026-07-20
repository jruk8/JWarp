# Contributing

Keep changes small and focused.

## Working on the plugin

- Run `./gradlew check` before opening a PR or handing the repo off.
- Keep command behavior, messages, and permissions aligned.
- Update `README.md` when you change user-facing commands or config keys.

## Code style

- Follow the existing Java style in `src/main/java`.
- Prefer small helpers over large command methods.
- Keep tests targeted at pure logic where possible.

## Pull requests

- Describe what changed and why.
- Call out any new permissions, config keys, or breaking command changes.
