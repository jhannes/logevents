package org.logevents.formatting;

public interface MdcFilter {

    boolean isKeyIncluded(String key);

}
