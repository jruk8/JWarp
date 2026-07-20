# JWarp Banner

![JWarp banner](JWarp-banner-1280x640.png)

# JWarp

JWarp is a small Paper plugin for managing named warps with per-warp permissions.

Commands:

| Command | Permission | Notes |
| --- | --- | --- |
| `/warp <warp-name>` | `jwarp.warp` | Teleport to a warp. Respects warp-specific permission and cooldown unless bypassed. |
| `/warps` | `jwarp.warps` | Lists configured warps. |
| `/jwarp reload` | `jwarp.reload` | Reloads `config.yml` and `messages.yml`. |
| `/jwarp help` | none | Shows the help text and available warps. |
| `/jwarp delwarp <warp-name>` | `jwarp.delwarp` | Deletes a warp. |
| `/jwarp setwarp <warp-name>` | `jwarp.setwarp` | Creates a warp at the current location. |
| `/jwarp redefine <warp-name>` | `jwarp.redefine` | Updates an existing warp to the current location. |
| `/jwarp setpermission <warp-name> <true/false>` | `jwarp.setpermission` | Enables or disables the warp's permission requirement. When enabled, it uses the configured permission template. |
| `/jwarp bypass` | `jwarp.bypass` | Toggles bypass for warp permissions and cooldowns. |
| `/jwarp info <warp>` | `jwarp.info` | Shows warp details, location, permission, and cooldown. |
| `/jwarp version` | `jwarp.version` | Shows plugin, server, and Java version details. |
| `/jw ...` | same as `/jwarp ...` | Short alias for `/jwarp`. |

The default permission node template is `jwarp.warp.{warp}`. You can change it in `config.yml`.
`config.yml` also controls the default success sound, warp cooldown, and warp actionbar display.
`messages.yml` uses MiniMessage formatting, not legacy `&` color codes.
See `CONTRIBUTING.md` for build instructions and contributor setup.
