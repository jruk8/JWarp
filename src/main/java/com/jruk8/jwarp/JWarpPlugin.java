package com.jruk8.jwarp;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class JWarpPlugin extends JavaPlugin implements CommandExecutor, Listener {
    private static final int CONFIG_VERSION = 2;
    private static final int MESSAGES_VERSION = 2;
    private static final String DEFAULT_PERMISSION_TEMPLATE = "jwarp.warp.{warp}";
    private static final String BASE_WARP_PERMISSION = "jwarp.warp";
    private static final String WARPS_PERMISSION = "jwarp.warps";
    private static final String RELOAD_PERMISSION = "jwarp.reload";
    private static final String SET_WARP_PERMISSION = "jwarp.setwarp";
    private static final String DEL_WARP_PERMISSION = "jwarp.delwarp";
    private static final String REDEFINE_PERMISSION = "jwarp.redefine";
    private static final String SET_PERMISSION_PERMISSION = "jwarp.setpermission";
    private static final String BYPASS_PERMISSION = "jwarp.bypass";

    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> actionBarClearTasks = new ConcurrentHashMap<>();
    private YamlFileUpdater fileUpdater;
    private WarpStore warpStore;
    private File messagesFile;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        fileUpdater = new YamlFileUpdater(this::getResource);
        updateConfigurationFiles();
        warpStore = new WarpStore(this);

        Objects.requireNonNull(getCommand("warp")).setExecutor(this);
        Objects.requireNonNull(getCommand("warps")).setExecutor(this);
        Objects.requireNonNull(getCommand("jwarp")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getOnlinePlayers().forEach(this::syncBypassState);
    }

    @Override
    public void onDisable() {
        bypassPlayers.clear();
        actionBarClearTasks.values().forEach(BukkitTask::cancel);
        actionBarClearTasks.clear();
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
        playSuccessSound(player);
        sendActionBar(player, message("warp.actionbar", Map.of("{warp}", warpName)));
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
        playSuccessSound(sender);
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

        updateConfigurationFiles();
        sender.sendMessage(message("jwarp.reload.success"));
        playSuccessSound(sender);
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
        playSuccessSound(sender);
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
        playSuccessSound(player);
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
        playSuccessSound(player);
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
                getConfig().getString("permission-template", DEFAULT_PERMISSION_TEMPLATE),
                warpName
            );
            warpStore.setPermission(warpName, permissionNode);
            sender.sendMessage(message("jwarp.setpermission.enabled", Map.of("{permission}", permissionNode)));
            playSuccessSound(sender);
            return true;
        }

        warpStore.setPermission(warpName, "");
        sender.sendMessage(message("jwarp.setpermission.disabled", Map.of("{warp}", warpName)));
        playSuccessSound(sender);
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
            playSuccessSound(player);
            return true;
        }

        bypassPlayers.add(player.getUniqueId());
        player.sendMessage(message("jwarp.bypass.enabled"));
        playSuccessSound(player);
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

    private void playSuccessSound(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return;
        }

        if (!getConfig().getBoolean("sound.enabled", true)) {
            return;
        }

        Sound sound = resolveSound(getConfig().getString("sound.name", "BLOCK_NOTE_BLOCK_PLING"));
        if (sound == null) {
            return;
        }

        float volume = (float) getConfig().getDouble("sound.volume", 1.0);
        float pitch = (float) getConfig().getDouble("sound.pitch", 1.0);
        player.playSound(player.getLocation(), sound, SoundCategory.PLAYERS, volume, pitch);
    }

    private Sound resolveSound(String soundName) {
        if (soundName == null || soundName.isBlank()) {
            return null;
        }

        NamespacedKey key = NamespacedKey.fromString(soundName.trim());
        if (key == null && soundName.indexOf(':') < 0) {
            key = NamespacedKey.fromString("minecraft:" + soundName.trim().toLowerCase(Locale.ROOT).replace('_', '.'));
        }

        return key == null ? null : Registry.SOUNDS.get(key);
    }

    private void sendActionBar(Player player, String actionBarMessage) {
        if (!getConfig().getBoolean("actionbar.enabled", true)) {
            return;
        }

        if (actionBarMessage == null || actionBarMessage.isBlank()) {
            return;
        }

        long lengthTicks = Math.max(1L, getConfig().getLong("actionbar.length-ticks", 40L));
        long fadeoutTicks = Math.max(0L, getConfig().getLong("actionbar.fadeout-ticks", 0L));
        long totalTicks = lengthTicks + fadeoutTicks;

        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(actionBarMessage));

        UUID playerId = player.getUniqueId();
        BukkitTask previousTask = actionBarClearTasks.remove(playerId);
        if (previousTask != null) {
            previousTask.cancel();
        }

        BukkitTask clearTask = getServer().getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) {
                player.sendActionBar(Component.empty());
            }
            actionBarClearTasks.remove(playerId);
        }, totalTicks);

        actionBarClearTasks.put(playerId, clearTask);
    }

    private void updateConfigurationFiles() {
        ensureDataFolder();

        fileUpdater.update(
            new File(getDataFolder(), "config.yml"),
            "config.yml",
            "config-version",
            CONFIG_VERSION,
            List.of(
                new YamlMigration(2, config -> {
                    String soundName = config.getString("sound.name", "");
                    if (soundName != null && isLegacySoundName(soundName)) {
                        config.set("sound.name", "minecraft:block.note_block.pling");
                        return true;
                    }

                    return false;
                })
            )
        );
        reloadConfig();

        messagesFile = new File(getDataFolder(), "messages.yml");
        fileUpdater.update(messagesFile, "messages.yml", "messages-version", MESSAGES_VERSION, List.of());
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void ensureDataFolder() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder.");
        }
    }

    private boolean isLegacySoundName(String soundName) {
        String normalized = soundName.trim().toUpperCase(Locale.ROOT);
        return normalized.equals("BLOCK_NOTE_BLOCK_PLING") || normalized.equals("NOTE_BLOCK_PLING");
    }
}
