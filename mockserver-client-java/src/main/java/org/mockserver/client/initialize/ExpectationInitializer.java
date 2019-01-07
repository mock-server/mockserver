package org.mockserver.client.initialize;

import org.mockserver.client.MockServerClient;

/**
 * @author jamesdbloom
 */
public interface ExpectationInitializer {

    public void initializeExpectations(MockServerClient mockServerClient);

}
