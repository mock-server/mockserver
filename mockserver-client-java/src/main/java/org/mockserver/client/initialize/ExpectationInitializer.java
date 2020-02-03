package org.mockserver.client.initialize;

import org.mockserver.client.MockServerClient;

/**
 * @author jamesdbloom
 * @deprecated use org.mockserver.client.initialize.PluginExpectationInitializer instead
 */
@Deprecated
public interface ExpectationInitializer {

    void initializeExpectations(MockServerClient mockServerClient);

}
