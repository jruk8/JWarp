package com.jruk8.jwarp;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

final class YamlFileUpdater {
    private final Function<String, InputStream> resourceLoader;

    YamlFileUpdater(Function<String, InputStream> resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    boolean update(
        File targetFile,
        String resourceName,
        String versionKey,
        int latestVersion,
        List<YamlMigration> migrations
    ) {
        try {
            YamlConfiguration defaults = loadDefaults(resourceName);
            YamlConfiguration current = targetFile.exists()
                ? YamlConfiguration.loadConfiguration(targetFile)
                : new YamlConfiguration();
            int currentVersion = current.contains(versionKey) ? current.getInt(versionKey, 0) : 0;

            boolean changed = mergeMissing(current, defaults);

            if (!current.contains(versionKey) || currentVersion < latestVersion) {
                List<YamlMigration> sortedMigrations = new ArrayList<>(migrations);
                sortedMigrations.sort(Comparator.comparingInt(YamlMigration::version));

                for (YamlMigration migration : sortedMigrations) {
                    if (migration.version() > currentVersion && migration.version() <= latestVersion) {
                        changed |= migration.apply(current);
                    }
                }

                current.set(versionKey, latestVersion);
                changed = true;
            }

            if (changed || !targetFile.exists()) {
                File parent = targetFile.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Could not create parent directory for " + targetFile.getName());
                }

                current.save(targetFile);
            }

            return changed;
        } catch (IOException exception) {
            throw new IllegalStateException("Could not update " + resourceName, exception);
        }
    }

    private YamlConfiguration loadDefaults(String resourceName) throws IOException {
        try (InputStream stream = resourceLoader.apply(resourceName)) {
            if (stream == null) {
                throw new IOException("Missing bundled resource: " + resourceName);
            }

            return YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
    }

    private boolean mergeMissing(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (!target.contains(key)) {
                if (defaults.isConfigurationSection(key)) {
                    ConfigurationSection created = target.createSection(key);
                    changed = true;
                    ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
                    if (defaultChild != null) {
                        changed |= mergeMissing(created, defaultChild);
                    }
                } else {
                    target.set(key, defaults.get(key));
                    changed = true;
                }
            } else if (defaults.isConfigurationSection(key) && target.isConfigurationSection(key)) {
                ConfigurationSection targetChild = target.getConfigurationSection(key);
                ConfigurationSection defaultChild = defaults.getConfigurationSection(key);
                if (targetChild != null && defaultChild != null) {
                    changed |= mergeMissing(targetChild, defaultChild);
                }
            }
        }

        return changed;
    }
}
