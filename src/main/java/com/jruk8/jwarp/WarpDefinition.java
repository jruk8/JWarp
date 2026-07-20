package com.jruk8.jwarp;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

record WarpDefinition(
    String worldName,
    double x,
    double y,
    double z,
    float yaw,
    float pitch,
    String permission
) {
    static WarpDefinition fromSection(ConfigurationSection section) {
        return new WarpDefinition(
            section.getString("world", ""),
            section.getDouble("x", 0.0),
            section.getDouble("y", 0.0),
            section.getDouble("z", 0.0),
            (float) section.getDouble("yaw", 0.0),
            (float) section.getDouble("pitch", 0.0),
            section.getString("permission", "")
        );
    }

    static WarpDefinition fromLocation(Location location, String permission) {
        World world = location.getWorld();
        return new WarpDefinition(
            world == null ? "" : world.getName(),
            location.getX(),
            location.getY(),
            location.getZ(),
            location.getYaw(),
            location.getPitch(),
            permission == null ? "" : permission
        );
    }

    WarpDefinition withLocation(Location location) {
        return fromLocation(location, permission);
    }

    WarpDefinition withPermission(String newPermission) {
        return new WarpDefinition(worldName, x, y, z, yaw, pitch, newPermission == null ? "" : newPermission);
    }

    void writeTo(ConfigurationSection section) {
        section.set("world", worldName);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
        section.set("permission", permission);
    }

    Location toLocation() {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }

        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }

        return new Location(world, x, y, z, yaw, pitch);
    }

    boolean requiresPermission() {
        return permission != null && !permission.isBlank();
    }
}
