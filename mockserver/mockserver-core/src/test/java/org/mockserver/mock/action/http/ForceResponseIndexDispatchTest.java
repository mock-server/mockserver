package org.mockserver.mock.action.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.ResponseMode;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.oneOf;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Drives the {@link Expectation#FORCE_RESPONSE_INDEX_HEADER} through the real matcher
 * ({@code RequestMatchers.firstMatchingExpectation}, which decides whether the request advances the
 * rotation) and the real {@link HttpActionHandler} dispatch (which selects the variant), so a regression in
 * either half of the plumbing is observable in the served status codes.
 */
public class ForceResponseIndexDispatchTest {

    private static final String PATH = "/force";

    private Scheduler scheduler;
    private HttpState httpState;
    private HttpActionHandler actionHandler;

    private static class CapturingResponseWriter extends ResponseWriter {
        private HttpResponse response;

        CapturingResponseWriter() {
            super(configuration(), new MockServerLogger());
        }

        @Override
        public void sendResponse(HttpRequest request, HttpResponse response) {
            this.response = response;
        }
    }

    @Before
    public void setUp() {
        Configuration configuration = configuration();
        MockServerLogger logger = new MockServerLogger(configuration, ForceResponseIndexDispatchTest.class);
        scheduler = new Scheduler(configuration, logger, true);
        httpState = new HttpState(configuration, logger, scheduler);
        actionHandler = new HttpActionHandler(configuration, null, httpState, null, null);
    }

    @After
    public void tearDown() {
        if (httpState != null) {
            httpState.stop();
        }
        scheduler.shutdown();
    }

    private void addSequence(ResponseMode responseMode) {
        httpState.add(new Expectation(request().withPath(PATH), Times.unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(Arrays.asList(
                response().withStatusCode(200),
                response().withStatusCode(500),
                response().withStatusCode(418)
            ))
            .withResponseMode(responseMode));
    }

    private int serve(String forcedIndexHeader) {
        HttpRequest request = request().withMethod("GET").withPath(PATH);
        if (forcedIndexHeader != null) {
            request.withHeader(Expectation.FORCE_RESPONSE_INDEX_HEADER, forcedIndexHeader);
        }
        CapturingResponseWriter writer = new CapturingResponseWriter();
        actionHandler.processAction(request, writer, null, new HashSet<>(), false, true);
        return writer.response.getStatusCode();
    }

    // --- SEQUENTIAL -------------------------------------------------------------------------

    @Test
    public void sequentialServesValidForcedIndexWithoutAdvancingRotation() {
        addSequence(ResponseMode.SEQUENTIAL);

        assertThat(serve(null), is(200));
        assertThat(serve("2"), is(418));
        assertThat(serve(" 0 "), is(200));
        // the two forced requests left the rotation where the normal request put it
        assertThat(serve(null), is(500));
        assertThat(serve(null), is(418));
    }

    @Test
    public void sequentialTreatsOutOfRangeIndexAsNormalRequest() {
        addSequence(ResponseMode.SEQUENTIAL);

        assertThat(serve("3"), is(200));
        // an ignored index advances the rotation exactly like a normal request
        assertThat(serve("99"), is(500));
        assertThat(serve(null), is(418));
    }

    @Test
    public void sequentialTreatsNegativeIndexAsNormalRequest() {
        addSequence(ResponseMode.SEQUENTIAL);

        assertThat(serve("-1"), is(200));
        assertThat(serve(null), is(500));
    }

    @Test
    public void sequentialTreatsNonNumericIndexAsNormalRequest() {
        addSequence(ResponseMode.SEQUENTIAL);

        assertThat(serve("abc"), is(200));
        assertThat(serve("1.0"), is(500));
        assertThat(serve(""), is(418));
        assertThat(serve(null), is(200));
    }

    @Test
    public void sequentialRotatesNormallyWithoutHeader() {
        addSequence(ResponseMode.SEQUENTIAL);

        assertThat(serve(null), is(200));
        assertThat(serve(null), is(500));
        assertThat(serve(null), is(418));
        assertThat(serve(null), is(200));
    }

    // --- RANDOM -----------------------------------------------------------------------------

    @Test
    public void randomAlwaysServesValidForcedIndex() {
        addSequence(ResponseMode.RANDOM);

        for (int i = 0; i < 30; i++) {
            assertThat(serve("1"), is(500));
        }
    }

    @Test
    public void randomSelectsNormallyForOutOfRangeNonNumericOrAbsentIndex() {
        addSequence(ResponseMode.RANDOM);

        for (String header : Arrays.asList("3", "-1", "abc", null)) {
            Set<Integer> served = new HashSet<>();
            for (int i = 0; i < 60; i++) {
                int statusCode = serve(header);
                // status-tolerance-ok: RANDOM mode serves one of the three variants, chosen per request
                assertThat(statusCode, is(oneOf(200, 500, 418)));
                served.add(statusCode);
            }
            // a pinned variant would serve one code; random selection over 3 variants serves several
            assertThat("header " + header, served.size(), greaterThan(1));
        }
    }

    // --- expectations without a forceable sequence ------------------------------------------

    @Test
    public void singleResponseExpectationIgnoresForcedIndex() {
        httpState.add(new Expectation(request().withPath(PATH), Times.unlimited(), TimeToLive.unlimited(), 0)
            .thenRespond(response().withStatusCode(201)));

        assertThat(serve("0"), is(201));
        assertThat(serve("5"), is(201));
        assertThat(serve(null), is(201));
    }
}
