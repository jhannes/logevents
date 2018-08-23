package org.logevents.util;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Properties;

public class Configuration {

    private Properties properties;
    private String prefix;

    public Configuration(Properties properties, String prefix) {
        this.properties = properties;
        this.prefix = prefix;
    }

    public URL getUrl(String key) {
        return toUrl(key, getString(key));
    }

    private URL toUrl(String key, String string) {
        try {
            return new URL(string);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(fullKey(key) + " value " + string + ": " + e.getMessage());
        }
    }

    public Optional<URL> optionalUrl(String key) {
        return optionalString(key).map(s -> toUrl(key, s));
    }


    public Optional<Integer> optionalInt(String key) {
        return optionalString(key).map(Integer::parseInt);
    }

    public Duration getDuration(String key) {
        try {
            return Duration.parse(getString(key));
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(fullKey(key) + " value " + getString(key) + ": " + e.getMessage());
        }
    }

    public String getString(String key) {
        return optionalString(key)
                .orElseThrow(() -> new IllegalArgumentException("Missing required key <" + fullKey(key) + "> in <" + properties.keySet() + ">"));
    }

    public boolean getBoolean(String key) {
        return optionalString(key)
                .map(Boolean::valueOf)
                .orElse(false);
    }

    public String[] getStringList(String key) {
        return optionalString(key)
                .map(s -> s.split(",\\s*"))
                .orElse(new String[0]);
    }

    public Optional<String> optionalString(String key) {
        return Optional.ofNullable(properties.getProperty(fullKey(key)));
    }

    private String fullKey(String key) {
        return prefix + "." + key;
    }

    public <T> T createInstance(String key, Class<T> clazz) {
        optionalString(key)
            .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + fullKey(key)));
        return ConfigUtil.create(fullKey(key), clazz.getPackage().getName(), properties);
    }

    public <T> T createInstance(String key, Class<T> clazz, String defaultPackage) {
        optionalString(key)
            .orElseThrow(() -> new IllegalArgumentException("Missing configuration for " + clazz.getSimpleName() + " in " + fullKey(key)));
        return ConfigUtil.create(fullKey(key), defaultPackage, properties);
    }

    public <T> T createInstanceWithDefault(String key, Class<T> defaultClass) {
        Class<?> clazz = ConfigUtil.getClass(fullKey(key), defaultClass.getPackage().getName(), properties)
                .orElse(defaultClass);
        return ConfigUtil.create(fullKey(key), clazz, properties);
    }

    public <T> T  createInstanceWithDefault(String key, Class<T> targetType, Class<? extends T> defaultClass) {
        Class<?> clazz = ConfigUtil.getClass(fullKey(key), targetType.getPackage().getName(), properties)
                .orElse(defaultClass);
        return ConfigUtil.create(fullKey(key), clazz, properties);
    }

    public String getPrefix() {
        return prefix;
    }

}
