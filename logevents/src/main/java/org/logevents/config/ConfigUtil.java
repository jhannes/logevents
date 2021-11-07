package org.logevents.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

public class ConfigUtil {

    public static Class<?> getClass(String key, String defaultPackage, String className) {
        if (!className.contains(".")) {
            className = defaultPackage + "." + className;
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new LogEventConfigurationException("Can't create " + key + "=" + className + ": " + e);
        }
    }

    public static <T> T create(String key, String defaultPackage, Optional<String> className, Map<String, String> properties) {
        Class<?> clazz = getClass(
                key,
                defaultPackage,
                className.orElseThrow(() -> new LogEventConfigurationException("Missing configuration for class in " + key))
        );
        return create(key, clazz, properties);
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(String key, Class<?> clazz, Map<String, String> properties) {
        try {
            try {
                return (T) clazz.getConstructor(Map.class, String.class).newInstance(properties, key);
            } catch (NoSuchMethodException e) {
                // For backwards compatibility
                try {
                    Constructor<?> constructor = clazz.getConstructor(Properties.class, String.class);
                    Properties props = new Properties();
                    props.putAll(properties);
                    return (T) constructor.newInstance(props, key);
                } catch (NoSuchMethodException e2) {
                    return (T) clazz.getConstructor().newInstance();
                }
            }
        } catch (LogEventConfigurationException e) {
            throw new LogEventConfigurationException(e.getMessage());
        } catch (RuntimeException e) {
            throw new LogEventConfigurationException("Exception when creating " + key + "=" + clazz.getName(), e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof LogEventConfigurationException) {
                throw (LogEventConfigurationException)e.getTargetException();
            }
            if (e.getTargetException() instanceof RuntimeException) {
                throw new LogEventConfigurationException("Exception when creating " + key + "=" + clazz.getName() + ": " + e.getTargetException(), e.getTargetException());
            }
            throw new LogEventConfigurationException("Exception when creating " + key + ": " + e.getTargetException());
        } catch (InstantiationException|IllegalAccessException|NoSuchMethodException e) {
            throw new LogEventConfigurationException("Can't create " + key + "=" + clazz.getName() + ": " + e);
        }
    }

}
