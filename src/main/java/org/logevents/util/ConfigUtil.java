package org.logevents.util;

import java.lang.reflect.InvocationTargetException;
import java.util.Properties;

public class ConfigUtil {

    @SuppressWarnings("unchecked")
    public static <T> T create(String prefix, String defaultPackage, Properties configuration) {
        String className = configuration.getProperty(prefix);
        if (className == null) {
            throw new IllegalArgumentException("Missing configuration for class in " + prefix);
        }
        if (!className.contains(".")) {
            className = defaultPackage + "." + className;
        }
        T o;
        try {
            Class<?> clazz = Class.forName(className);
            try {
                o = (T) clazz.getConstructor(Properties.class, String.class).newInstance(configuration, prefix);
            } catch (NoSuchMethodException e) {
                o = (T) clazz.newInstance();
            } catch (InvocationTargetException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                return null;
            }
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Can't create " + prefix + ": " + e);
        } catch (InstantiationException|SecurityException|IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return null;
        }

        return o;
    }

}
