package io.ryhunwashere.auditlogger.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class Config {
    private final Properties props = new Properties();

    public Config(String propertiesPath) {
        try (InputStream input = getClass().getResourceAsStream(propertiesPath)) {
            if (input == null) {
                throw new RuntimeException("Resource not found: " + propertiesPath);
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getString(String key) {
        return props.getProperty(key);
    }

    public String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public int getInt(String key) {
        return Integer.parseInt(props.getProperty(key));
    }

    public int getInt(String key, int defaultValue) {
        return Integer.parseInt(props.getProperty(key, Integer.toString(defaultValue)));
    }

    public long getLong(String key) {
        return Long.parseLong(props.getProperty(key));
    }

    public long getLong(String key, long defaultValue) {
        return Long.parseLong(props.getProperty(key, Long.toString(defaultValue)));
    }
}