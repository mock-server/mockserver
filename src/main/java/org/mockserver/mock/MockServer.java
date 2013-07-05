package org.mockserver.mock;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.ModelObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServer extends ModelObject {

    protected final List<Expectation> expectations = new ArrayList<Expectation>();

    public Expectation when(HttpRequest httpRequest) {
        return when(httpRequest, Times.unlimited());
    }

    public Expectation when(final HttpRequest httpRequest, Times times) {
        Expectation expectation;
        if (times.isUnlimited()) {
            Collection<Expectation> existingExpectationsWithMatchingRequest = Collections2.filter(expectations, new Predicate<Expectation>() {
                public boolean apply(Expectation expectation) {
                    return expectation.contains(httpRequest);
                }
            });
            if (!existingExpectationsWithMatchingRequest.isEmpty()) {
                for (Expectation existingExpectation : existingExpectationsWithMatchingRequest) {
                    existingExpectation.setNotUnlimitedResponses();
                }
                expectation = new Expectation(httpRequest, Times.once());
            } else {
                expectation = new Expectation(httpRequest, Times.unlimited());
            }
        } else {
            expectation = new Expectation(httpRequest, times);
        }
        expectations.add(expectation);
        return expectation;
    }

    public HttpResponse handle(HttpRequest httpRequest) {
        for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
            if (expectation.matches(httpRequest)) {
                if (!expectation.getTimes().greaterThenZero()) {
                    expectations.remove(expectation);
                }
                return expectation.getHttpResponse();
            }
        }
        return null;
    }

    public void clear(HttpRequest httpRequest) {
        for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
            if (expectation.matches(httpRequest)) {
                expectations.remove(expectation);
            }
        }
    }
}
