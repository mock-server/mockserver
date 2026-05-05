package org.mockserver.server.initialize;

import org.mockserver.mock.Expectation;

/**
 * @author jamesdbloom
 */
public interface ExpectationInitializer {

    Expectation[] initializeExpectations();

}
