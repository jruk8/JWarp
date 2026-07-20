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
                config-version: 2
                sound:
                  enabled: true
                  name: "minecraft:block.note_block.pling"
                  volume: 1.0
                  pitch: 1.0
                actionbar:
                  enabled: true
                  length-ticks: 40
                  fadeout-ticks: 0
                warps: {}
                """,
            "messages.yml",
            """
                messages-version: 2
                prefix: "&8[&bJWarp&8] "
                warp:
                  actionbar: "&eWarping to &f{warp}&e"
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
            2,
            List.of(new YamlMigration(2, config -> {
                if ("BLOCK_NOTE_BLOCK_PLING".equalsIgnoreCase(config.getString("sound.name"))) {
                    config.set("sound.name", "minecraft:block.note_block.pling");
                    return true;
                }

                return false;
            }))
        );

        YamlConfiguration updated = YamlConfiguration.loadConfiguration(configFile);
        assertTrue(changed);
        assertEquals(2, updated.getInt("config-version"));
        assertEquals(false, updated.getBoolean("sound.enabled"));
        assertEquals("minecraft:block.note_block.pling", updated.getString("sound.name"));
        assertEquals(1.0, updated.getDouble("sound.volume"));
        assertEquals("keep", updated.getString("custom.value"));
        assertTrue(updated.contains("actionbar.length-ticks"));
        assertTrue(updated.contains("actionbar.fadeout-ticks"));
    }

    private InputStream stream(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
