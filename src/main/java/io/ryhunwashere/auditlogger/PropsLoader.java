package io.ryhunwashere.auditlogger;

import org.jetbrains.annotations.NotNull;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class PropsLoader {
    private static Properties props;

    public static void loadProperties(String propertiesPath) {
        props = new Properties();
        try {
            props.load(new FileInputStream(propertiesPath));
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
