package com.jruk8.jwarp;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Optional;

final class WarpStore {
    private final JavaPlugin plugin;

    WarpStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    List<String> names() {
        ConfigurationSection section = warpsSection();
        if (section == null) {
            return List.of();
        }

        return section.getKeys(false).stream()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .toList();
    }

    Optional<WarpDefinition> find(String warpName) {
        ConfigurationSection section = warpSection(warpName);
        if (section == null) {
            return Optional.empty();
        }

        return Optional.of(WarpDefinition.fromSection(section));
    }

    boolean exists(String warpName) {
        return find(warpName).isPresent();
    }

    void setWarp(String warpName, Location location) {
        save(warpName, WarpDefinition.fromLocation(location, ""));
    }

    void redefineWarp(String warpName, Location location) {
        WarpDefinition existing = find(warpName).orElseThrow();
        save(warpName, existing.withLocation(location));
    }

    void setPermission(String warpName, String permission) {
        WarpDefinition existing = find(warpName).orElseThrow();
        save(warpName, existing.withPermission(permission));
    }

    boolean delete(String warpName) {
        String key = resolveKey(warpName);
        if (warpsSection() == null) {
            return false;
        }

        plugin.getConfig().set("warps." + key, null);
        plugin.saveConfig();
        return true;
    }

    String resolveKey(String warpName) {
        String normalized = WarpPermissions.normalizeWarpName(warpName);
        ConfigurationSection section = warpsSection();
        if (section == null) {
            return normalized;
        }

        for (String key : section.getKeys(false)) {
            if (key.equalsIgnoreCase(normalized)) {
                return key;
            }
        }

        return normalized;
    }

    private void save(String warpName, WarpDefinition definition) {
        String key = resolveKey(warpName);
        plugin.getConfig().set("warps." + key, null);
        ConfigurationSection section = plugin.getConfig().createSection("warps." + key);
        definition.writeTo(section);
        plugin.saveConfig();
    }

    private ConfigurationSection warpsSection() {
        return plugin.getConfig().getConfigurationSection("warps");
    }

    private ConfigurationSection warpSection(String warpName) {
        return plugin.getConfig().getConfigurationSection("warps." + resolveKey(warpName));
    }
}
