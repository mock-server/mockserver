package org.mockserver.extension;

import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;

/**
 * What a server gives an {@link Extension} when it starts. Extensions are trusted code.
 */
public interface ExtensionContext {

    /**
     * @return the server's live configuration; changing it from an extension is not supported
     */
    Configuration configuration();

    /**
     * @return the server's logger, whose events appear in the server's log
     */
    MockServerLogger logger();
}
