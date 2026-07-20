# JWarp

JWarp is a small Paper plugin for managing named warps with per-warp permissions.

Commands:

- `/warp <warp-name>` teleports to a warp.
- `/warps` lists configured warps.
- `/jwarp reload` reloads `config.yml` and `messages.yml`.
- `/jwarp setwarp <warp-name>` creates a warp at the current location.
- `/jwarp redefine <warp-name>` updates an existing warp to the current location.
- `/jwarp delwarp <warp-name>` deletes a warp.
- `/jwarp setpermission <warp-name> <true/false>` enables or disables the warp's permission requirement.
- `/jwarp bypass` toggles bypass for warps that require permission.

The default permission node template is `jwarp.warp.{warp}`. You can change it in `config.yml`.
`config.yml` also controls the default success sound and the warp actionbar display.
The plugin now merges new config/message keys on startup and reload, and applies
small migrations when bundled defaults change shape.

Build from the repository root:

- `./gradlew build`
- `./gradlew check`

The project uses Gradle and Java 25.
