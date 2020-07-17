package org.mockserver.stop;

import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class Stop {

    public static void stopQuietly(Stoppable stoppable) {
        if (stoppable != null) {
            try {
                stoppable.stop();
            } catch (Throwable throwable) {
                LoggerFactory.getLogger(Stop.class).debug("Exception stopping " + stoppable, throwable);
            }
        }
    }
}
