package org.logevents;

import org.slf4j.Logger;
import org.slf4j.event.Level;

public interface LoggerConfiguration extends Logger {

    Level getLevelThreshold();

    String getObserver();

}
