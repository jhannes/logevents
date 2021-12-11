package org.logevents.config;

public interface MdcFilter {

    boolean isKeyIncluded(String key);

}
