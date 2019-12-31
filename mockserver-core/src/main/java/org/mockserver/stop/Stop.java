package org.mockserver.stop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jamesdbloom
 */
public class Stop {

    private static final Logger logger = LoggerFactory.getLogger(Stop.class);

    public static void stopQuietly(Stoppable stoppable) {
        if (stoppable != null) {
            try {
                stoppable.stop();
            } catch (Throwable throwable) {
                logger.debug("Exception stopping " + stoppable, throwable);
            }
        }
    }
}
