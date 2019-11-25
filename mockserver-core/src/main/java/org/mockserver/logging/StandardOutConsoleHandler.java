package org.mockserver.logging;

import java.util.logging.ConsoleHandler;

public class StandardOutConsoleHandler extends ConsoleHandler {

    public StandardOutConsoleHandler() {
        super();
        setOutputStream(System.out);
    }

}
