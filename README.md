![JWarp banner](JWarp-banner-1280x640.png)

# JWarp

JWarp is a small Paper plugin for managing named warps with per-warp permissions.

Download: https://modrinth.com/plugin/jwarp

Contribute: https://github.com/jruk8/JWarp

License: GPL 3.0

## Commands:

| Command | Permission | Notes |
| --- | --- | --- |
| `/warp <warp-name>` | `jwarp.warp` | Teleport to a warp. Respects warp-specific permission and cooldown unless bypassed. |
| `/warps` | `jwarp.warps` | Lists configured warps. |
| `/jwarp reload` | `jwarp.admin.reload` | Reloads `config.yml` and `messages.yml`. |
| `/jwarp help` | none | Shows the help text and available warps. |
| `/jwarp delwarp <warp-name>` | `jwarp.admin.delwarp` | Deletes a warp. |
| `/jwarp setwarp <warp-name>` | `jwarp.admin.setwarp` | Creates a warp at the current location. |
| `/jwarp redefine <warp-name>` | `jwarp.admin.redefine` | Updates an existing warp to the current location. |
| `/jwarp setpermission <warp-name> <true/false>` | `jwarp.admin.setpermission` | Enables or disables the warp's permission requirement. When enabled, it uses the configured permission template. |
| `/jwarp bypass` | `jwarp.admin.bypass` | Toggles bypass for warp permissions and cooldowns. |
| `/jwarp info <warp>` | `jwarp.admin.info` | Shows warp details, location, permission, and cooldown. |
| `/jwarp version` | `jwarp.admin.version` | Shows plugin, server, and Java version details. |
| `/jw ...` | same as `/jwarp ...` | Short alias for `/jwarp`. |

The default permission node template is `jwarp.warp.{warp}`. You can change it in `config.yml`.

Admin-only commands are grouped under `jwarp.admin.*`, so a single wildcard grant covers all management permissions.
`config.yml` also controls the default success sound, warp cooldown, and warp actionbar display.
`messages.yml` uses MiniMessage formatting, not legacy `&` color codes.
See `CONTRIBUTING.md` for build instructions and contributor setup.
