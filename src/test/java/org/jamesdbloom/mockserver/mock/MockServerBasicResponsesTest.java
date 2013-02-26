package org.jamesdbloom.mockserver.mock;

import org.jamesdbloom.mockserver.mappers.ExpectationMapper;
import org.jamesdbloom.mockserver.matchers.HttpRequestMatcher;
import org.jamesdbloom.mockserver.model.*;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerBasicResponsesTest {

    @Mock
    private ExpectationMapper expectationMapper;

    @InjectMocks
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

        initMocks(this);
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
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withPath("somepath"))).thenReturn(httpRequestMatcher.withPath("somepath"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void respondWhenRegexPathMatches() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withPath("[a-z]*"))).thenReturn(httpRequestMatcher.withPath("[a-z]*"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath")));
    }

    @Test
    public void doNotRespondWhenPathDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withPath("somepath"))).thenReturn(httpRequestMatcher.withPath("somepath"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withPath("someotherpath")));
    }

    @Test
    public void doNotRespondWhenRegexPathDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withPath("[a-z]*"))).thenReturn(httpRequestMatcher.withPath("[a-z]*"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withPath("someotherpath123")));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalHeaders() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withPath("somepath"))).thenReturn(httpRequestMatcher.withPath("somepath"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalQueryParameters() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withPath("somepath"))).thenReturn(httpRequestMatcher.withPath("somepath"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatches() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBody("somebody"))).thenReturn(httpRequestMatcher.withBody("somebody"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void respondWhenRegexBodyMatches() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBody("[a-z]*"))).thenReturn(httpRequestMatcher.withBody("[a-z]*"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody")));
    }

    @Test
    public void doNotRespondWhenBodyDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBody("somebody"))).thenReturn(httpRequestMatcher.withBody("somebody"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBody("someotherBody")));
    }

    @Test
    public void doNotRespondWhenRegexBodyDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBody("[a-z]*"))).thenReturn(httpRequestMatcher.withBody("[a-z]*"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBody("someotherBody123")));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalHeaders() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBody("somebody"))).thenReturn(httpRequestMatcher.withBody("somebody"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalQueryParameters() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBody("somebody"))).thenReturn(httpRequestMatcher.withBody("somebody"));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBody("somebody").withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenHeaderMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withHeaders(new Header("name", "value")))).thenReturn(httpRequestMatcher.withHeaders(new Header("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withHeaders(new Header("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withHeaders(new Header("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withHeaders(new Header("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesHasMissingValue() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withHeaders(new Header("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withHeaders(new Header("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndExtraHeaders() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withHeaders(new Header("name", "value")))).thenReturn(httpRequestMatcher.withHeaders(new Header("name", "value")));

        // when
        mockServer.when(httpRequest.withHeaders(new Header("name", "value"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withHeaders(new Header("nameExtra", "valueExtra"), new Header("name", "value"), new Header("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndPathDifferent() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withHeaders(new Header("name", "value")))).thenReturn(httpRequestMatcher.withHeaders(new Header("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenQueryParameterMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withQueryParameters(new Parameter("name", "value")))).thenReturn(httpRequestMatcher.withQueryParameters(new Parameter("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenQueryParameterWithMultipleValuesMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withQueryParameters(new Parameter("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2"))).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenQueryParameterWithMultipleValuesDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withQueryParameters(new Parameter("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenQueryParameterWithMultipleValuesHasMissingValue() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withQueryParameters(new Parameter("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withQueryParameters(new Parameter("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("name", "value1"))));
    }

    @Test
    public void respondWhenQueryParameterMatchesAndExtraParameters() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withQueryParameters(new Parameter("name", "value")))).thenReturn(httpRequestMatcher.withQueryParameters(new Parameter("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withQueryParameters(new Parameter("nameExtra", "valueExtra"), new Parameter("name", "value"), new Parameter("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenQueryParameterMatchesAndPathDifferent() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withQueryParameters(new Parameter("name", "value")))).thenReturn(httpRequestMatcher.withQueryParameters(new Parameter("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withQueryParameters(new Parameter("name", "value"))));
    }


    @Test
    public void respondWhenBodyParameterMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBodyParameters(new Parameter("name", "value")))).thenReturn(httpRequestMatcher.withBodyParameters(new Parameter("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParameterWithMultipleValuesMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBodyParameters(new Parameter("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withBodyParameters(new Parameter("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenBodyParameterWithMultipleValuesDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBodyParameters(new Parameter("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withBodyParameters(new Parameter("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenBodyParameterWithMultipleValuesHasMissingValue() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBodyParameters(new Parameter("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withBodyParameters(new Parameter("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("name", "value1"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndExtraParameters() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBodyParameters(new Parameter("name", "value")))).thenReturn(httpRequestMatcher.withBodyParameters(new Parameter("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withBodyParameters(new Parameter("nameExtra", "valueExtra"), new Parameter("name", "value"), new Parameter("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndPathDifferent() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withBodyParameters(new Parameter("name", "value")))).thenReturn(httpRequestMatcher.withBodyParameters(new Parameter("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withBodyParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenCookieMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withCookies(new Cookie("name", "value")))).thenReturn(httpRequestMatcher.withCookies(new Cookie("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void respondWhenCookieWithMultipleValuesMatchesExactly() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withCookies(new Cookie("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withCookies(new Cookie("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenCookieWithMultipleValuesDoesNotMatch() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withCookies(new Cookie("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withCookies(new Cookie("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenCookieWithMultipleValuesHasMissingValue() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withCookies(new Cookie("name", "value1", "value2")))).thenReturn(httpRequestMatcher.withCookies(new Cookie("name", "value1", "value2")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertNull(mockServer.handle(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void respondWhenCookieMatchesAndExtraCookies() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withCookies(new Cookie("name", "value")))).thenReturn(httpRequestMatcher.withCookies(new Cookie("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withCookies(new Cookie("nameExtra", "valueExtra"), new Cookie("name", "value"), new Cookie("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenCookieMatchesAndPathDifferent() {
        // given
        when(expectationMapper.transformsToMatcher(httpRequest.withCookies(new Cookie("name", "value")))).thenReturn(httpRequestMatcher.withCookies(new Cookie("name", "value")));

        // when
        mockServer.when(httpRequest).respond(httpResponse.withBody("somebody"));

        // then
        assertEquals(httpResponse, mockServer.handle(new HttpRequest().withPath("somepath").withCookies(new Cookie("name", "value"))));
    }
}
