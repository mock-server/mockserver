package org.jamesdbloom.mockserver.mock;

import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.model.*;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * @author jamesdbloom
 */
public class MockServerBasicResponsesTest {

    private MockServer mockServer;
    private HttpRequest httpRequest;
    private HttpRequestMatcher httpRequestMatcher;
    private HttpResponse httpResponse;

    @Before
    public void prepareTestFixture() {
        httpRequest = new HttpRequest();
        httpRequestMatcher = new HttpRequestMatcher();
        httpResponse = new HttpResponse();
        mockServer = new MockServer();
    }

    @Test
    public void addsExpectation() {
        // given
        Expectation expectation = mock(Expectation.class);

        // when
        mockServer.addExpectation(expectation);

        // then
        assertSame(expectation, mockServer.expectations.get(0));
    }

    @Test
    public void respondWhenPathMatches() {
        // when
        mockServer.when(httpRequest.withPath("somepath")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenRegexPathMatches() {
        // when
        mockServer.when(httpRequest.withPath("[a-z]*")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void doNotRespondWhenPathDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withPath("somepath")).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withPath("someotherpath")));
    }

    @Test
    public void doNotRespondWhenRegexPathDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withPath("[a-z]*")).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withPath("someotherpath123")));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalHeaders() {
        // when
        mockServer.when(httpRequest.withPath("somepath")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalQueryParameters() {
        // when
        mockServer.when(httpRequest.withPath("somepath")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatches() {
        // when
        mockServer.when(httpRequest.withBody("somebody")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void respondWhenRegexBodyMatches() {
        // when
        mockServer.when(httpRequest.withBody("[a-z]*")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doNotRespondWhenBodyDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withBody("somebody")).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBody("someotherBody")));
    }

    @Test
    public void doNotRespondWhenRegexBodyDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withBody("[a-z]*")).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBody("someotherBody123")));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalHeaders() {
        // when
        mockServer.when(httpRequest.withBody("somebody")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalQueryParameters() {
        // when
        mockServer.when(httpRequest.withBody("somebody")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody").withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenHeaderMatchesExactly() {
        // when
        mockServer.when(httpRequest.withHeaders(new Header("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withHeaders(new Header("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withHeaders(new Header("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesHasMissingValue() {
        // when
        mockServer.when(httpRequest.withHeaders(new Header("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndExtraHeaders() {
        // when
        mockServer.when(httpRequest.withHeaders(new Header("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withHeaders(new Header("nameExtra", "valueExtra"), new Header("name", "value"), new Header("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndPathDifferent() {
        // when
        mockServer.when(httpRequest.withHeaders(new Header("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenQueryParameterMatchesExactly() {
        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenQueryParameterWithMultipleValuesMatchesExactly() {
        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenQueryParameterWithMultipleValuesDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenQueryParameterWithMultipleValuesHasMissingValue() {
        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value1"))));
    }

    @Test
    public void respondWhenQueryParameterMatchesAndExtraParameters() {
        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("nameExtra", "valueExtra"), new Parameter("name", "value"), new Parameter("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenQueryParameterMatchesAndPathDifferent() {
        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesExactly() {
        // when
        mockServer.when(httpRequest.withBodyParameters(new Parameter("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParameterWithMultipleValuesMatchesExactly() {
        // when
        mockServer.when(httpRequest.withBodyParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenBodyParameterWithMultipleValuesDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withBodyParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenBodyParameterWithMultipleValuesHasMissingValue() {
        // when
        mockServer.when(httpRequest.withBodyParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value1"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndExtraParameters() {
        // when
        mockServer.when(httpRequest.withBodyParameters(new Parameter("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("nameExtra", "valueExtra"), new Parameter("name", "value"), new Parameter("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndPathDifferent() {
        // when
        mockServer.when(httpRequest.withBodyParameters(new Parameter("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withBodyParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenCookieMatchesExactly() {
        // when
        mockServer.when(httpRequest.withCookies(new Cookie("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void respondWhenCookieWithMultipleValuesMatchesExactly() {
        // when
        mockServer.when(httpRequest.withCookies(new Cookie("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenCookieWithMultipleValuesDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withCookies(new Cookie("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenCookieWithMultipleValuesHasMissingValue() {
        // when
        mockServer.when(httpRequest.withCookies(new Cookie("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void respondWhenCookieMatchesAndExtraCookies() {
        // when
        mockServer.when(httpRequest.withCookies(new Cookie("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withCookies(new Cookie("nameExtra", "valueExtra"), new Cookie("name", "value"), new Cookie("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenCookieMatchesAndPathDifferent() {
        // when
        mockServer.when(httpRequest.withCookies(new Cookie("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }
}
