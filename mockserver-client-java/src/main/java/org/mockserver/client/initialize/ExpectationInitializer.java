package org.mockserver.client.initialize;

import org.mockserver.client.MockServerClient;

/**
 * @author jamesdbloom
 * @deprecated instead use org.mockserver.client.initialize.PluginExpectationInitializer or org.mockserver.server.initialize.ExpectationInitializer
 */
@Deprecated
public interface ExpectationInitializer {

    void initializeExpectations(MockServerClient mockServerClient);

}
