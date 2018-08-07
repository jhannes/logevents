package org.logevents.util;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.Properties;

public class ConfigUtil {

    public static <T> T create(String prefix, String defaultPackage, Properties properties) {
        Class<?> clazz = getClass(prefix, defaultPackage, properties)
                .orElseThrow(() -> new IllegalArgumentException("Missing configuration for class in " + prefix));
        return create(prefix, clazz, properties);
    }

    public static Optional<Class<?>> getClass(String prefix, String defaultPackage, Properties properties) {
        String className = properties.getProperty(prefix);
        if (className == null) {
            return Optional.empty();
        }
        if (!className.contains(".")) {
            className = defaultPackage + "." + className;
        }
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Can't create " + prefix + "=" + className + ": " + e);
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(String prefix, Class<?> clazz, Properties properties) {
        try {
            try {
                return (T) clazz.getConstructor(Properties.class, String.class).newInstance(properties, prefix);
            } catch (NoSuchMethodException e) {
                return (T) clazz.newInstance();
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Exception when creating " + prefix + "=" + clazz.getName() + ": " + e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException) {
                throw new IllegalArgumentException("Exception when creating " + prefix + "=" + clazz.getName(), e.getTargetException());
            }
            throw new IllegalArgumentException("Exception when creating " + prefix + e);
        } catch (InstantiationException|IllegalAccessException e) {
            throw new IllegalArgumentException("Can't create " + prefix + "=" + clazz.getName() + ": " + e);
        }
    }

}
