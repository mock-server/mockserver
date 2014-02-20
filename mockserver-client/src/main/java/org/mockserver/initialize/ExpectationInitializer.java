package org.mockserver.initialize;

import org.mockserver.client.server.MockServerClient;

/**
 * @author jamesdbloom
 */
public interface ExpectationInitializer {

    public void initializeExpectations(MockServerClient mockServerClient);

}
