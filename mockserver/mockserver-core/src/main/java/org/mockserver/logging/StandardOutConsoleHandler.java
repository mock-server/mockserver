package org.mockserver.logging;

import java.util.logging.*;

public class StandardOutConsoleHandler extends StreamHandler {

    public StandardOutConsoleHandler() {
        setOutputStream(System.out);
    }

    @Override
    public void publish(LogRecord record) {
        super.publish(record);
        flush();
    }

    @Override
    public void close() {
        flush();
    }

}
