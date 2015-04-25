package org.mockserver.logging;

import ch.qos.logback.core.rolling.RollingFileAppender;

/**
 * @author jamesdbloom
 */
public class DelayedFileCreationRollingFileAppender<E> extends RollingFileAppender<E> {

    private boolean fileCreated = false;

    @Override
    public void start() {
        started = true;
    }

    @Override
    protected void subAppend(E event) {
        if (!fileCreated) {
            super.start();
            fileCreated = true;
        }
        super.subAppend(event);
    }
}
