package org.mockserver.mock;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.time.FixedTime;
import org.mockserver.time.TimeService;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableDateTimeString.after;
import static org.mockserver.model.NottableDateTimeString.before;
import static org.mockserver.model.NottableDateTimeString.equalToDateTime;
import static org.mockserver.model.NottableString.string;

/**
 * Date-time matchers routed through the {@link RequestMatchers} engine as a running server would, on
 * headers, query-string parameters, path parameters and cookies, built both in Java and from JSON.
 */
public class RequestMatcherDateTimeComparisonTest {

    @ClassRule
    public static final FixedTime fixedTime = new FixedTime();

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        requestMatchers = new RequestMatchers(configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
    }

    private static String minutesFromNow(long minutes) {
        return TimeService.FIXED_INSTANT_FOR_TESTS.plus(Duration.ofMinutes(minutes)).toString();
    }

    @Test
    public void shouldMatchHeaderAgainstNowWithOffset() {
        Expectation expectation = new Expectation(request().withPath("/token").withHeader(string("X-Issued-At"), after("now -1 hours")))
            .thenRespond(response().withBody("fresh"));
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/token").withHeader("X-Issued-At", minutesFromNow(-30))), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/token").withHeader("X-Issued-At", minutesFromNow(-90))), is(nullValue()));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/token").withHeader("X-Issued-At", "yesterday")), is(nullValue()));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/token")), is(nullValue()));
    }

    @Test
    public void shouldMatchRfc1123Header() {
        Expectation expectation = new Expectation(request().withPath("/cache").withHeader(string("If-Modified-Since"), before("2026-10-21T00:00:00Z").withActualFormat("rfc1123")))
            .thenRespond(response().withStatusCode(304));
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/cache").withHeader("If-Modified-Since", "Tue, 20 Oct 2026 08:00:00 GMT")), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/cache").withHeader("If-Modified-Since", "Thu, 22 Oct 2026 08:00:00 GMT")), is(nullValue()));
    }

    @Test
    public void shouldMatchQueryStringParameter() {
        Expectation expectation = new Expectation(request().withPath("/reports").withQueryStringParameter(string("from"), after("2026-01-01").withActualFormat("dd/MM/yyyy")))
            .thenRespond(response().withBody("reports"));
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/reports").withQueryStringParameter("from", "02/01/2026")), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/reports").withQueryStringParameter("from", "31/12/2025")), is(nullValue()));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/reports").withQueryStringParameter("from", "31/02/2026")), is(nullValue()));
    }

    @Test
    public void shouldMatchPathParameter() {
        Expectation expectation = new Expectation(request().withPath("/orders/{day}").withPathParameter(string("day"), equalToDateTime("2026-10-21")))
            .thenRespond(response().withBody("orders"));
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/orders/2026-10-21")), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/orders/2026-10-22")), is(nullValue()));
    }

    @Test
    public void shouldMatchCookie() {
        Expectation expectation = new Expectation(request().withPath("/session").withCookie(string("expires"), after("now")))
            .thenRespond(response().withBody("valid"));
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/session").withCookie("expires", minutesFromNow(5))), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/session").withCookie("expires", minutesFromNow(-5))), is(nullValue()));
    }

    @Test
    public void shouldMatchAnExpectationWrittenInJson() {
        Expectation expectation = new ExpectationSerializer(new MockServerLogger()).deserialize("{" +
            "  \"httpRequest\": {" +
            "    \"path\": \"/json\"," +
            "    \"headers\": { \"X-Issued-At\": [ { \"after\": \"now -1 hours\" } ] }," +
            "    \"queryStringParameters\": { \"until\": [ { \"not\": true, \"before\": \"now\" } ] }" +
            "  }," +
            "  \"httpResponse\": { \"body\": \"json\" }" +
            "}");
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/json").withHeader("X-Issued-At", minutesFromNow(-30)).withQueryStringParameter("until", minutesFromNow(10))), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/json").withHeader("X-Issued-At", minutesFromNow(-30)).withQueryStringParameter("until", minutesFromNow(-10))), is(nullValue()));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/json").withHeader("X-Issued-At", minutesFromNow(-30)).withQueryStringParameter("until", "soon")), is(nullValue()));
    }

    @Test
    public void shouldKeepAPlainValueThatLooksLikeADateTimeMatcherLiteral() {
        Expectation expectation = new Expectation(request().withPath("/literal").withHeader("X-Note", "before:2026-01-01").withQueryStringParameter("q", "after:now"))
            .thenRespond(response().withBody("literal"));
        requestMatchers.add(expectation, API);

        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/literal").withHeader("X-Note", "before:2026-01-01").withQueryStringParameter("q", "after:now")), is(expectation));
        assertThat(requestMatchers.firstMatchingExpectation(request().withPath("/literal").withHeader("X-Note", "2025-01-01").withQueryStringParameter("q", "after:now")), is(nullValue()));
    }
}
