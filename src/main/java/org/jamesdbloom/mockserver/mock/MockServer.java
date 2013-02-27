package org.jamesdbloom.mockserver.mock;

import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.Times;
import org.jamesdbloom.mockserver.model.HttpRequest;
import org.jamesdbloom.mockserver.model.HttpResponse;
import org.jamesdbloom.mockserver.model.ModelObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class MockServer extends ModelObject {

    protected final List<Expectation> expectations = new ArrayList<Expectation>();
    private ExpectationMapper expectationMapper = new ExpectationMapper();

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
        for (Expectation expectation : expectations) {
            if (expectation.matches(httpRequest)) {
                return expectation.getHttpResponse();
            }
        }
        return null;
    }

    public void addExpectation(Expectation expectation) {
        expectations.add(expectation);
    }
}
