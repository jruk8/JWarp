package com.jruk8.jwarp;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JWarpPlugin extends JavaPlugin implements CommandExecutor, Listener {
    private static final String BASE_WARP_PERMISSION = "jwarp.warp";
    private static final String WARPS_PERMISSION = "jwarp.warps";
    private static final String RELOAD_PERMISSION = "jwarp.reload";
    private static final String SET_WARP_PERMISSION = "jwarp.setwarp";
    private static final String DEL_WARP_PERMISSION = "jwarp.delwarp";
    private static final String REDEFINE_PERMISSION = "jwarp.redefine";
    private static final String SET_PERMISSION_PERMISSION = "jwarp.setpermission";
    private static final String BYPASS_PERMISSION = "jwarp.bypass";

    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
    private WarpStore warpStore;
    private File messagesFile;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        warpStore = new WarpStore(this);
        reloadMessages();

        Objects.requireNonNull(getCommand("warp")).setExecutor(this);
        Objects.requireNonNull(getCommand("warps")).setExecutor(this);
        Objects.requireNonNull(getCommand("jwarp")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getOnlinePlayers().forEach(this::syncBypassState);
    }

    @Override
    public void onDisable() {
        bypassPlayers.clear();
        messages = null;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        syncBypassState(event.getPlayer());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        return switch (commandName) {
            case "warp" -> handleWarp(sender, args);
            case "warps" -> handleWarps(sender);
            case "jwarp" -> handleJWarp(sender, args);
            default -> false;
        };
    }

    private boolean handleWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("command.player-only"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(message("warp.usage"));
            return true;
        }

        if (!player.hasPermission(BASE_WARP_PERMISSION)) {
            player.sendMessage(message("warp.no-permission"));
            return true;
        }

        String warpName = WarpPermissions.normalizeWarpName(args[0]);
        WarpDefinition warp = warpStore.find(warpName).orElse(null);
        if (warp == null) {
            player.sendMessage(message("warp.missing", Map.of("{warp}", warpName)));
            return true;
        }

        if (warp.requiresPermission() && !hasBypass(player) && !player.hasPermission(warp.permission())) {
            player.sendMessage(message("warp.permission-required", Map.of("{permission}", warp.permission())));
            return true;
        }

        Location location = warp.toLocation();
        if (location == null) {
            player.sendMessage(message("warp.invalid-world", Map.of("{warp}", warpName)));
            return true;
        }

        player.teleport(location);
        player.sendMessage(message("warp.teleported", Map.of("{warp}", warpName)));
        return true;
    }

    private boolean handleWarps(CommandSender sender) {
        if (!sender.hasPermission(WARPS_PERMISSION)) {
            sender.sendMessage(message("warps.no-permission"));
            return true;
        }

        List<String> warps = warpStore.names();
        if (warps.isEmpty()) {
            sender.sendMessage(message("warps.empty"));
            return true;
        }

        sender.sendMessage(message("warps.header", Map.of("{count}", Integer.toString(warps.size()))));
        sender.sendMessage(message("warps.list", Map.of("{warps}", String.join(", ", warps))));
        return true;
    }

    private boolean handleJWarp(CommandSender sender, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(message("jwarp.usage"));
            return true;
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> handleReload(sender, args);
            case "delwarp" -> handleDeleteWarp(sender, args);
            case "setwarp" -> handleSetWarp(sender, args);
            case "redefine" -> handleRedefineWarp(sender, args);
            case "setpermission" -> handleSetPermission(sender, args);
            case "bypass" -> handleBypass(sender, args);
            default -> {
                sender.sendMessage(message("jwarp.usage"));
                yield true;
            }
        };
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(message("jwarp.reload.usage"));
            return true;
        }

        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            sender.sendMessage(message("jwarp.reload.no-permission"));
            return true;
        }

        reloadConfig();
        reloadMessages();
        sender.sendMessage(message("jwarp.reload.success"));
        return true;
    }

    private boolean handleDeleteWarp(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(message("jwarp.delwarp.usage"));
            return true;
        }

        if (!sender.hasPermission(DEL_WARP_PERMISSION)) {
            sender.sendMessage(message("jwarp.delwarp.no-permission"));
            return true;
        }

        String warpName = WarpPermissions.normalizeWarpName(args[1]);
        if (!warpStore.exists(warpName)) {
            sender.sendMessage(message("jwarp.delwarp.missing", Map.of("{warp}", warpName)));
            return true;
        }

        warpStore.delete(warpName);
        sender.sendMessage(message("jwarp.delwarp.success", Map.of("{warp}", warpName)));
        return true;
    }

    private boolean handleSetWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("command.player-only"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(message("jwarp.setwarp.usage"));
            return true;
        }

        if (!player.hasPermission(SET_WARP_PERMISSION)) {
            player.sendMessage(message("jwarp.setwarp.no-permission"));
            return true;
        }

        String warpName = WarpPermissions.normalizeWarpName(args[1]);
        if (warpStore.exists(warpName)) {
            player.sendMessage(message("jwarp.setwarp.exists", Map.of("{warp}", warpName)));
            return true;
        }

        warpStore.setWarp(warpName, player.getLocation());
        player.sendMessage(message("jwarp.setwarp.success", Map.of("{warp}", warpName)));
        return true;
    }

    private boolean handleRedefineWarp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("command.player-only"));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(message("jwarp.redefine.usage"));
            return true;
        }

        if (!player.hasPermission(REDEFINE_PERMISSION)) {
            player.sendMessage(message("jwarp.redefine.no-permission"));
            return true;
        }

        String warpName = WarpPermissions.normalizeWarpName(args[1]);
        if (!warpStore.exists(warpName)) {
            player.sendMessage(message("jwarp.redefine.missing", Map.of("{warp}", warpName)));
            return true;
        }

        warpStore.redefineWarp(warpName, player.getLocation());
        player.sendMessage(message("jwarp.redefine.success", Map.of("{warp}", warpName)));
        return true;
    }

    private boolean handleSetPermission(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(message("jwarp.setpermission.usage"));
            return true;
        }

        if (!sender.hasPermission(SET_PERMISSION_PERMISSION)) {
            sender.sendMessage(message("jwarp.setpermission.no-permission"));
            return true;
        }

        String warpName = WarpPermissions.normalizeWarpName(args[1]);
        if (!warpStore.exists(warpName)) {
            sender.sendMessage(message("jwarp.setpermission.missing", Map.of("{warp}", warpName)));
            return true;
        }

        Boolean enabled = parseBoolean(args[2]);
        if (enabled == null) {
            sender.sendMessage(message("jwarp.setpermission.usage"));
            return true;
        }

        if (enabled) {
            String permissionNode = WarpPermissions.resolvePermissionNode(
                getConfig().getString("permission-template", "jwarp.warp.{warp}"),
                warpName
            );
            warpStore.setPermission(warpName, permissionNode);
            sender.sendMessage(message("jwarp.setpermission.enabled", Map.of("{permission}", permissionNode)));
            return true;
        }

        warpStore.setPermission(warpName, "");
        sender.sendMessage(message("jwarp.setpermission.disabled", Map.of("{warp}", warpName)));
        return true;
    }

    private boolean handleBypass(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("command.player-only"));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(message("jwarp.bypass.usage"));
            return true;
        }

        if (!player.hasPermission(BYPASS_PERMISSION)) {
            player.sendMessage(message("jwarp.bypass.no-permission"));
            return true;
        }

        if (hasBypass(player)) {
            bypassPlayers.remove(player.getUniqueId());
            player.sendMessage(message("jwarp.bypass.disabled"));
            return true;
        }

        bypassPlayers.add(player.getUniqueId());
        player.sendMessage(message("jwarp.bypass.enabled"));
        return true;
    }

    private void syncBypassState(Player player) {
        if (player.isOp()) {
            bypassPlayers.add(player.getUniqueId());
            return;
        }

        bypassPlayers.remove(player.getUniqueId());
    }

    private boolean hasBypass(Player player) {
        return bypassPlayers.contains(player.getUniqueId());
    }

    private Boolean parseBoolean(String input) {
        if (input == null) {
            return null;
        }

        return switch (input.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "on" -> true;
            case "false", "no", "off" -> false;
            default -> null;
        };
    }

    private void reloadMessages() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder.");
        }

        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String message(String path) {
        return message(path, Map.of());
    }

    private String message(String path, Map<String, String> replacements) {
        String raw = messages.getString(path, path);
        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            raw = raw.replace(entry.getKey(), entry.getValue());
        }

        String prefix = MessageFormatter.colorize(messages.getString("prefix", ""));
        return MessageFormatter.format(raw, prefix);
    }
}
