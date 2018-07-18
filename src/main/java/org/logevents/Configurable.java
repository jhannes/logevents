package org.logevents;

import java.util.Properties;

public interface Configurable {

    void configure(Properties configuration, String prefix);

}
