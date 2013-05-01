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

    public Expectation when(final HttpRequest httpRequest) {
        Collection<Expectation> existingExpectationsWithMatchingRequest = Collections2.filter(expectations, new Predicate<Expectation>() {
            public boolean apply(Expectation expectation) {
                return expectation.contains(httpRequest);
            }
        });
        if (!existingExpectationsWithMatchingRequest.isEmpty()) {
            for (Expectation expectation : existingExpectationsWithMatchingRequest) {
                expectation.setNotUnlimitedResponses();
            }
            return when(httpRequest, Times.once());
        } else {
            return when(httpRequest, Times.unlimited());
        }
    }

    public Expectation when(HttpRequest httpRequest, Times times) {
        Expectation expectation = new Expectation(httpRequest, times);
        expectations.add(expectation);
        return expectation;
    }

    public HttpResponse handle(HttpRequest httpRequest) {
        for (Expectation expectation : new ArrayList<Expectation>(expectations)) {
            if (expectation.matches(httpRequest)) {
                if(!expectation.getTimes().greaterThenZero()) {
                    expectations.remove(expectation);
                }
                return expectation.getHttpResponse();
            }
        }
        return null;
    }

    public void addExpectation(Expectation expectation) {
        expectations.add(expectation);
    }
}
