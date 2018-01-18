package org.mockserver.log.model;

import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;

/**
 * @author jamesdbloom
 */
public class ExpectationMatchLogEntry extends LogEntry implements ExpectationLogEntry {

    private final Expectation expectation;

    public ExpectationMatchLogEntry(HttpRequest httpRequest, Expectation expectation) {
        super(httpRequest);
        this.expectation = expectation.clone();
    }

    public Expectation getExpectation() {
        return expectation;
    }

}
