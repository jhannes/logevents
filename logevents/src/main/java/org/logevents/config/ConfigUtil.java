package org.logevents.config;

import java.lang.reflect.InvocationTargetException;
import java.util.Optional;
import java.util.Properties;

public class ConfigUtil {

        public static Optional<Class<?>> getClass(String prefix, String defaultPackage, String className) {
        if (className == null) {
            return Optional.empty();
        }
        if (!className.contains(".")) {
            className = defaultPackage + "." + className;
        }
        try {
            return Optional.of(Class.forName(className));
        } catch (ClassNotFoundException e) {
            throw new LogEventConfigurationException("Can't create " + prefix + "=" + className + ": " + e);
        }
    }

    public static <T> T create(String prefix, String defaultPackage, String className, Properties properties) {
        Class<?> clazz = getClass(prefix, defaultPackage, className)
                .orElseThrow(() -> new LogEventConfigurationException("Missing configuration for class in " + prefix));
        return create(prefix, clazz, properties);
    }

    @SuppressWarnings("unchecked")
    public static <T> T create(String prefix, Class<?> clazz, Properties properties) {
        try {
            try {
                return (T) clazz.getConstructor(Properties.class, String.class).newInstance(properties, prefix);
            } catch (NoSuchMethodException e) {
                return (T) clazz.getConstructor().newInstance();
            }
        } catch (LogEventConfigurationException e) {
            throw new LogEventConfigurationException(e.getMessage());
        } catch (RuntimeException e) {
            throw new LogEventConfigurationException("Exception when creating " + prefix + "=" + clazz.getName(), e);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof LogEventConfigurationException) {
                throw (LogEventConfigurationException)e.getTargetException();
            }
            if (e.getTargetException() instanceof RuntimeException) {
                throw new LogEventConfigurationException("Exception when creating " + prefix + "=" + clazz.getName() + ": " + e.getTargetException(), e.getTargetException());
            }
            throw new LogEventConfigurationException("Exception when creating " + prefix + ": " + e.getTargetException());
        } catch (InstantiationException|IllegalAccessException|NoSuchMethodException e) {
            throw new LogEventConfigurationException("Can't create " + prefix + "=" + clazz.getName() + ": " + e);
        }
    }

}
