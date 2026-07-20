package com.jruk8.jwarp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFileUpdaterTest {
    @TempDir
    Path tempDir;

    @Test
    void mergesMissingKeysAndAppliesMigrations() throws Exception {
        Map<String, String> resources = Map.of(
            "config.yml",
            """
                config-version: 3
                sound:
                  enabled: true
                  name: "minecraft:block.note_block.pling"
                  volume: 1.0
                  pitch: 1.0
                warp-cooldown: 3.0
                actionbar:
                  enabled: true
                  length-ticks: 40
                  fadeout-ticks: 0
                warps: {}
                """,
            "messages.yml",
            """
                messages-version: 4
                prefix: "<dark_gray>[<aqua>JWarp<dark_gray>] "
                warp:
                  actionbar: "<yellow>Warping to <white>{warp}<yellow>"
                """);

        YamlFileUpdater updater = new YamlFileUpdater(name -> stream(resources.get(name)));

        File configFile = tempDir.resolve("config.yml").toFile();
        YamlConfiguration userConfig = new YamlConfiguration();
        userConfig.set("config-version", 1);
        userConfig.set("sound.enabled", false);
        userConfig.set("sound.name", "BLOCK_NOTE_BLOCK_PLING");
        userConfig.set("custom.value", "keep");
        userConfig.save(configFile);

        boolean changed = updater.update(
            configFile,
            "config.yml",
            "config-version",
            3,
            List.of(new YamlMigration(3, config -> {
                if ("BLOCK_NOTE_BLOCK_PLING".equalsIgnoreCase(config.getString("sound.name"))) {
                    config.set("sound.name", "minecraft:block.note_block.pling");
                    return true;
                }

                if (!config.contains("warp-cooldown")) {
                    config.set("warp-cooldown", 3.0);
                    return true;
                }

                return false;
            }))
        );

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(changed);
        assertEquals(3, updated.getInt("config-version"));
        assertEquals(false, updated.getBoolean("sound.enabled"));
        assertEquals("minecraft:block.note_block.pling", updated.getString("sound.name"));
        assertEquals(1.0, updated.getDouble("sound.volume"));
        assertEquals(3.0, updated.getDouble("warp-cooldown"));
        assertEquals("keep", updated.getString("custom.value"));
        assertTrue(updated.contains("actionbar.length-ticks"));
        assertTrue(updated.contains("actionbar.fadeout-ticks"));
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
