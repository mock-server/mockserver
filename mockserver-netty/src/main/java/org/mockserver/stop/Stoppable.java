package org.mockserver.stop;

import java.util.concurrent.Future;

/**
 * @author jamesdbloom
 */
public interface Stoppable {

    Future<?> stop();

}
