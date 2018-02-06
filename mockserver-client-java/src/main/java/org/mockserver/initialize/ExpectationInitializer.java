package org.mockserver.initialize;

import org.mockserver.client.MockServerClient;

/**
 * @author jamesdbloom
 */
public interface ExpectationInitializer {

    public void initializeExpectations(MockServerClient mockServerClient);

}
