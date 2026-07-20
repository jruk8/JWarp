package com.jruk8.jwarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Locale;
import java.util.Objects;

public final class JWarpPlugin extends JavaPlugin implements CommandExecutor {
    private File messagesFile;
    private YamlConfiguration messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadMessages();

        Objects.requireNonNull(getCommand("testwarp")).setExecutor(this);
        Objects.requireNonNull(getCommand("jwarp")).setExecutor(this);
    }

    @Override
    public void onDisable() {
        messages = null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("testwarp")) {
            return handleTestWarp(sender);
        }

        if (commandName.equals("jwarp")) {
            return handleJWarp(sender, args);
        }

        return false;
    }

    private boolean handleTestWarp(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(message("testwarp.console-only"));
            return true;
        }

        Location location = createTestWarpLocation(player);
        player.teleport(location);
        player.sendMessage(message("testwarp.teleported"));
        return true;
    }

    private boolean handleJWarp(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            reloadMessages();
            sender.sendMessage(message("reload.success"));
            return true;
        }

        sender.sendMessage(message("reload.usage"));
        return true;
    }

    private Location createTestWarpLocation(Player player) {
        TestWarpTarget target = new TestWarpTarget(
            getConfig().getString("testwarp.world", ""),
            getConfig().getDouble("testwarp.x", 0.0),
            getConfig().getDouble("testwarp.y", 100.0),
            getConfig().getDouble("testwarp.z", 0.0),
            (float) getConfig().getDouble("testwarp.yaw", player.getLocation().getYaw()),
            (float) getConfig().getDouble("testwarp.pitch", player.getLocation().getPitch())
        );

        World world = target.worldName() == null || target.worldName().isBlank()
            ? player.getWorld()
            : Bukkit.getWorld(target.worldName());

        if (world == null) {
            world = player.getWorld();
        }

        return new Location(world, target.x(), target.y(), target.z(), target.yaw(), target.pitch());
    }

    private void reloadMessages() {
        if (!getDataFolder().exists()) {
            getDataFolder().mkdirs();
        }

        messagesFile = new File(getDataFolder(), "messages.yml");

        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }

        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private String message(String path) {
        String raw = messages.getString(path, path);
        String prefix = color(messages.getString("prefix", ""));
        return MessageFormatter.format(raw, prefix);
    }

    private String color(String input) {
        return MessageFormatter.colorize(input);
    }
}