package com.jruk8.jwarp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class JWarpPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    private static final int CONFIG_VERSION = 3;
    private static final int MESSAGES_VERSION = 3;
    private static final String DEFAULT_PERMISSION_TEMPLATE = "jwarp.warp.{warp}";
    private static final String BASE_WARP_PERMISSION = "jwarp.warp";
    private static final String WARPS_PERMISSION = "jwarp.warps";
    private static final String RELOAD_PERMISSION = "jwarp.reload";
    private static final String SET_WARP_PERMISSION = "jwarp.setwarp";
    private static final String DEL_WARP_PERMISSION = "jwarp.delwarp";
    private static final String REDEFINE_PERMISSION = "jwarp.redefine";
    private static final String SET_PERMISSION_PERMISSION = "jwarp.setpermission";
    private static final String INFO_PERMISSION = "jwarp.info";
    private static final String VERSION_PERMISSION = "jwarp.version";
    private static final String BYPASS_PERMISSION = "jwarp.bypass";

    private final Set<UUID> bypassPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> actionBarClearTasks = new ConcurrentHashMap<>();
    private final WarpCooldownTracker cooldownTracker = new WarpCooldownTracker();
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
        Objects.requireNonNull(getCommand("warp")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("warps")).setExecutor(this);
        Objects.requireNonNull(getCommand("warps")).setTabCompleter(this);
        Objects.requireNonNull(getCommand("jwarp")).setExecutor(this);
        Objects.requireNonNull(getCommand("jwarp")).setTabCompleter(this);
        getServer().getPluginManager().registerEvents(this, this);

        getServer().getOnlinePlayers().forEach(this::syncBypassState);
    }

    @Override
    public void onDisable() {
        bypassPlayers.clear();
        actionBarClearTasks.values().forEach(BukkitTask::cancel);
        actionBarClearTasks.clear();
        cooldownTracker.clearAll();
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
            case "jwarp", "jw" -> handleJWarp(sender, args);
            default -> false;
        };
    }

    @Override
    public List<String> onTabComplete(
        CommandSender sender,
        Command command,
        String alias,
        String[] args
    ) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        if ("warp".equals(commandName)) {
            return tabCompleteWarps(args);
        }

        if ("jwarp".equals(commandName)) {
            return tabCompleteJWarp(sender, args);
        }

        return List.of();
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

        if (!hasBypass(player) && isOnCooldown(player)) {
            player.sendMessage(
                message("warp.cooldown", Map.of("{seconds}", formatSeconds(remainingCooldownSeconds(player))))
            );
            return true;
        }

        Location location = warp.toLocation();
        if (location == null) {
            player.sendMessage(message("warp.invalid-world", Map.of("{warp}", warpName)));
            return true;
        }

        player.teleport(location);
        cooldownTracker.markWarp(player.getUniqueId(), System.currentTimeMillis());
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
            return handleHelp(sender);
        }

        String subcommand = args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "reload" -> handleReload(sender, args);
            case "delwarp" -> handleDeleteWarp(sender, args);
            case "setwarp" -> handleSetWarp(sender, args);
            case "redefine" -> handleRedefineWarp(sender, args);
            case "setpermission" -> handleSetPermission(sender, args);
            case "bypass" -> handleBypass(sender, args);
            case "info" -> handleInfo(sender, args);
            case "version" -> handleVersion(sender, args);
            default -> handleHelp(sender);
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

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (args.length != 2) {
            sender.sendMessage(message("jwarp.info.usage"));
            return true;
        }

        if (!sender.hasPermission(INFO_PERMISSION)) {
            sender.sendMessage(message("jwarp.info.no-permission"));
            return true;
        }

        String warpName = WarpPermissions.normalizeWarpName(args[1]);
        WarpDefinition warp = warpStore.find(warpName).orElse(null);
        if (warp == null) {
            sender.sendMessage(message("jwarp.info.missing", Map.of("{warp}", warpName)));
            return true;
        }

        sender.sendMessage(message("jwarp.info.header", Map.of("{warp}", warpName)));
        sender.sendMessage(message("jwarp.info.world", Map.of(
            "{world}",
            warp.worldName().isBlank() ? "current world" : warp.worldName()
        )));
        sender.sendMessage(message("jwarp.info.position", Map.of(
            "{x}", formatCoordinate(warp.x()),
            "{y}", formatCoordinate(warp.y()),
            "{z}", formatCoordinate(warp.z())
        )));
        sender.sendMessage(message("jwarp.info.permission", Map.of(
            "{permission}",
            warp.requiresPermission() ? warp.permission() : "none"
        )));
        playSuccessSound(sender);
        return true;
    }

    private boolean handleVersion(CommandSender sender, String[] args) {
        if (args.length != 1) {
            sender.sendMessage(message("jwarp.version.usage"));
            return true;
        }

        if (!sender.hasPermission(VERSION_PERMISSION)) {
            sender.sendMessage(message("jwarp.version.no-permission"));
            return true;
        }

        sender.sendMessage(message("jwarp.version.message", Map.of(
            "{version}", getDescription().getVersion(),
            "{server}", getServer().getVersion(),
            "{java}", System.getProperty("java.version", "unknown")
        )));
        playSuccessSound(sender);
        return true;
    }

    private boolean handleHelp(CommandSender sender) {
        sender.sendMessage(message("jwarp.help.header"));
        sender.sendMessage(message("jwarp.help.alias"));
        sender.sendMessage(message("jwarp.help.commands"));
        for (String commandLine : availableCommandLines(sender)) {
            sender.sendMessage(message("jwarp.help.command-line", Map.of("{command}", commandLine)));
        }
        sender.sendMessage(message("jwarp.help.warps"));
        sender.sendMessage(message("jwarp.help.warps", Map.of("{warps}", String.join(", ", warpStore.names()))));
        return true;
    }

    private List<String> availableCommandLines(CommandSender sender) {
        List<String> commands = new ArrayList<>();
        if (sender.hasPermission(BASE_WARP_PERMISSION)) {
            commands.add("/warp <warp-name> [" + BASE_WARP_PERMISSION + "]");
        }
        if (sender.hasPermission(WARPS_PERMISSION)) {
            commands.add("/warps [" + WARPS_PERMISSION + "]");
        }
        if (sender.hasPermission(RELOAD_PERMISSION)) {
            commands.add("/jwarp reload [" + RELOAD_PERMISSION + "]");
        }

        commands.add("/jwarp help");

        if (sender.hasPermission(DEL_WARP_PERMISSION)) {
            commands.add("/jwarp delwarp <warp-name> [" + DEL_WARP_PERMISSION + "]");
        }
        if (sender.hasPermission(SET_WARP_PERMISSION)) {
            commands.add("/jwarp setwarp <warp-name> [" + SET_WARP_PERMISSION + "]");
        }
        if (sender.hasPermission(REDEFINE_PERMISSION)) {
            commands.add("/jwarp redefine <warp-name> [" + REDEFINE_PERMISSION + "]");
        }
        if (sender.hasPermission(SET_PERMISSION_PERMISSION)) {
            commands.add("/jwarp setpermission <warp-name> <true/false> [" + SET_PERMISSION_PERMISSION + "]");
        }
        if (sender.hasPermission(BYPASS_PERMISSION)) {
            commands.add("/jwarp bypass [" + BYPASS_PERMISSION + "]");
        }
        if (sender.hasPermission(INFO_PERMISSION)) {
            commands.add("/jwarp info <warp> [" + INFO_PERMISSION + "]");
        }
        if (sender.hasPermission(VERSION_PERMISSION)) {
            commands.add("/jwarp version [" + VERSION_PERMISSION + "]");
        }

        return commands;
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

    private boolean isOnCooldown(Player player) {
        return cooldownTracker.isOnCooldown(player.getUniqueId(), System.currentTimeMillis(), getCooldownMillis());
    }

    private double remainingCooldownSeconds(Player player) {
        return cooldownTracker.remainingMillis(
            player.getUniqueId(),
            System.currentTimeMillis(),
            getCooldownMillis()
        ) / 1000.0;
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

        Sound sound = resolveSound(getConfig().getString("sound.name", "minecraft:block.note_block.pling"));
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
        if (key == null) {
            key = legacySoundKey(soundName.trim());
        }

        return key == null ? null : Registry.SOUNDS.get(key);
    }

    private NamespacedKey legacySoundKey(String soundName) {
        String normalized = soundName.toUpperCase(Locale.ROOT);
        if (normalized.equals("BLOCK_NOTE_BLOCK_PLING") || normalized.equals("NOTE_BLOCK_PLING")) {
            return NamespacedKey.fromString("minecraft:block.note_block.pling");
        }

        return null;
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
                new YamlMigration(3, config -> {
                    boolean changed = false;

                    String soundName = config.getString("sound.name", "");
                    if (soundName != null && isLegacySoundName(soundName)) {
                        config.set("sound.name", "minecraft:block.note_block.pling");
                        changed = true;
                    }

                    if (!config.contains("warp-cooldown")) {
                        config.set("warp-cooldown", 3.0);
                        changed = true;
                    }

                    return changed;
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

    private long getCooldownMillis() {
        return Math.max(0L, Math.round(getConfig().getDouble("warp-cooldown", 3.0) * 1000.0));
    }

    private String formatCoordinate(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private String formatSeconds(double seconds) {
        return String.format(Locale.ROOT, "%.1f", seconds);
    }

    private List<String> tabCompleteWarps(String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return warpStore.names().stream()
            .filter(warp -> warp.startsWith(prefix))
            .collect(Collectors.toList());
    }

    private List<String> tabCompleteJWarp(CommandSender sender, String[] args) {
        if (args.length == 0) {
            return List.of();
        }

        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return availableSubcommands(sender).stream()
                .filter(subcommand -> subcommand.startsWith(prefix))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && isWarpNameSubcommand(args[0])) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return warpStore.names().stream()
                .filter(warp -> warp.startsWith(prefix))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && "setpermission".equalsIgnoreCase(args[0])) {
            return List.of("true", "false");
        }

        return List.of();
    }

    private List<String> availableSubcommands(CommandSender sender) {
        List<String> subcommands = new ArrayList<>();
        subcommands.add("reload");
        subcommands.add("help");
        subcommands.add("delwarp");
        subcommands.add("setwarp");
        subcommands.add("redefine");
        subcommands.add("setpermission");
        subcommands.add("bypass");
        subcommands.add("info");
        subcommands.add("version");

        if (!sender.hasPermission(RELOAD_PERMISSION)) {
            subcommands.remove("reload");
        }
        if (!sender.hasPermission(DEL_WARP_PERMISSION)) {
            subcommands.remove("delwarp");
        }
        if (!sender.hasPermission(SET_WARP_PERMISSION)) {
            subcommands.remove("setwarp");
        }
        if (!sender.hasPermission(REDEFINE_PERMISSION)) {
            subcommands.remove("redefine");
        }
        if (!sender.hasPermission(SET_PERMISSION_PERMISSION)) {
            subcommands.remove("setpermission");
        }
        if (!sender.hasPermission(BYPASS_PERMISSION)) {
            subcommands.remove("bypass");
        }
        if (!sender.hasPermission(INFO_PERMISSION)) {
            subcommands.remove("info");
        }
        if (!sender.hasPermission(VERSION_PERMISSION)) {
            subcommands.remove("version");
        }

        return subcommands;
    }

    private boolean isWarpNameSubcommand(String input) {
        return "delwarp".equalsIgnoreCase(input)
            || "setwarp".equalsIgnoreCase(input)
            || "redefine".equalsIgnoreCase(input)
            || "info".equalsIgnoreCase(input)
            || "setpermission".equalsIgnoreCase(input);
    }
}
