package io.ryhunwashere.auditlogger.util;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class PropsLoader {
    private static final Map<String, Config> configs = new HashMap<>();
    private static boolean initialized = false;

    // Load all configs at once
    public static void initialize(Map<String, String> configFiles) {
        if (initialized) {
            System.err.println("PropsLoader is already initialized!");
            return;
        }
        for (Map.Entry<String, String> entry : configFiles.entrySet()) {
            String name = entry.getKey();
            String path = entry.getValue();
            configs.put(name, new Config(path));
        }
        initialized = true;
    }

    public static @NotNull Config getConfig(String name) {
        if (!initialized)
            throw new IllegalStateException("PropsLoader is not initialized. Call initialize() first.");
        Config config = configs.get(name);
        if (config == null)
            throw new RuntimeException("Config not found: " + name);
        return config;
    }
}
