package org.mockserver.initializer;

import org.mockserver.mock.Expectation;

/**
 * @author jamesdbloom
 */
public interface ExpectationInitializer {

    public Expectation[] initializeExpectations();

}
