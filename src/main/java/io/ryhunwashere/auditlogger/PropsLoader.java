package io.ryhunwashere.auditlogger;

import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class PropsLoader {
    private static Properties props;

    public static void loadProperties(String propertiesPath) {
        props = new Properties();
        try (InputStream input = PropsLoader.class.getResourceAsStream(propertiesPath)) {
            if (input == null) {
                throw new FileNotFoundException("Resource not found: " + propertiesPath);
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String getString(String key) {
        return props.getProperty(key);
    }

    public static String getString(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    public static int getInt(String key) {
        return Integer.parseInt(props.getProperty(key));
    }

    public static int getInt(String key, @NotNull Integer defaultValue) {
        return Integer.parseInt(props.getProperty(key, defaultValue.toString()));
    }

    public static long getLong(String key) {
        return Long.parseLong(props.getProperty(key));
    }

    public static long getLong(String key, @NotNull Long defaultValue) {
        return Long.parseLong(props.getProperty(key, defaultValue.toString()));
    }
}
