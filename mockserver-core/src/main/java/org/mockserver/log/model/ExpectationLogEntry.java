package org.mockserver.log.model;

import org.mockserver.mock.Expectation;

public interface ExpectationLogEntry {

    Expectation getExpectation();
}
