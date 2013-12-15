package org.mockserver.mock;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.model.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
    public void respondWhenPathMatchesAndAdditionalParameters() {
        // when
        mockServer.when(httpRequest.withPath("somepath")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withQueryString("name=value")));
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
    public void respondWhenBodyMatchesAndAdditionalParameters() {
        // when
        mockServer.when(httpRequest.withBody("somebody")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody").withQueryString("name=value")));
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
    public void respondWhenParameterMatchesExactly() {
        // when
        mockServer.when(httpRequest.withQueryString("name=value")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryString("name=value")));
    }

    @Test
    public void respondWhenParameterWithMultipleValuesMatchesExactly() {
        // when
        mockServer.when(httpRequest.withQueryString("name=value1&name=value2")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryString("name=value1&name=value2")));
    }

    @Test
    public void doNotRespondWhenParameterWithMultipleValuesDoesNotMatch() {
        // when
        mockServer.when(httpRequest.withQueryString("name=value1&name=value2")).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withQueryString("name=value1&name=value3")));
    }

    @Test
    public void doNotRespondWhenParameterWithMultipleValuesHasMissingValue() {
        // when
        mockServer.when(httpRequest.withQueryString("name=value1&name=value2")).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withQueryString("name=value1")));
    }

    @Test
    @Ignore
    public void respondWhenParameterMatchesAndExtraParameters() {
        // when
        mockServer.when(httpRequest.withQueryString("name=value")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryString("nameExtra=valueExtra&name=value&nameExtraExtra=valueExtraExtra")));
    }

    @Test
    public void respondWhenParameterMatchesAndPathDifferent() {
        // when
        mockServer.when(httpRequest.withQueryString("name=value")).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withQueryString("name=value")));
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
