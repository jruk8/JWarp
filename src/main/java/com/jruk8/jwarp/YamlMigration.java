package com.jruk8.jwarp;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.function.Function;

record YamlMigration(int version, Function<YamlConfiguration, Boolean> action) {
    boolean apply(YamlConfiguration configuration) {
        return Boolean.TRUE.equals(action.apply(configuration));
    }
}
