package org.mockserver.extension;

import org.mockserver.configuration.Configuration;

/**
 * An {@link Extension} told each time the server's configuration is updated through
 * {@code PUT /mockserver/configuration} (WireMock's {@code GlobalSettingsListener}). Calling {@link Configuration}
 * setters directly in embedded mode does not notify it.
 * <p>
 * {@link #onConfigurationUpdated} runs on the extension's own listener thread after the update has been applied. A
 * throw is logged and counted.
 */
public interface SettingsListener extends Extension {

    /**
     * @param configuration the server's live configuration, after the update; changing it here is not supported
     */
    void onConfigurationUpdated(Configuration configuration);
}
