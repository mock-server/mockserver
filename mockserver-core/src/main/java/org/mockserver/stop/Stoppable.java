package org.mockserver.stop;

import java.io.Closeable;

/**
 * @author jamesdbloom
 */
public interface Stoppable extends Closeable {

    void stop();

}
