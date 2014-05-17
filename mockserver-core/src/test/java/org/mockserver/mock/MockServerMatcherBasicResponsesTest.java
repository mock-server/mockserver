package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.model.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherBasicResponsesTest {

    private MockServerMatcher mockServerMatcher;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;

    @Before
    public void prepareTestFixture() {
        httpRequest = new HttpRequest();
        httpResponse = new HttpResponse();
        mockServerMatcher = new MockServerMatcher();
    }

    @Test
    public void respondWhenPathMatches() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void respondWhenRegexPathMatches() {
        // when
        mockServerMatcher.when(httpRequest.withPath("[a-zA-Z]*")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doNotRespondWhenPathDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withPath("someOtherPath")));
    }

    @Test
    public void doNotRespondWhenRegexPathDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withPath("[a-z]*")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withPath("someOtherPath123")));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalHeaders() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalQueryStringParameters() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalBodyParameters() {
        // when
        mockServerMatcher.when(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenBodyMatches() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new StringBody("someBody", Body.Type.EXACT))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new StringBody("someBody", Body.Type.EXACT))));
    }

    @Test
    public void respondWhenRegexBodyMatches() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new StringBody("[a-zA-Z]*", Body.Type.REGEX))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new StringBody("someBody", Body.Type.EXACT))));
    }

    @Test
    public void doNotRespondWhenBodyDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new StringBody("someBody", Body.Type.EXACT))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withBody(new StringBody("someOtherBody", Body.Type.EXACT))));
    }

    @Test
    public void doNotRespondWhenRegexBodyDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new StringBody("[a-z]*", Body.Type.REGEX))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withBody(new StringBody("someOtherBody123", Body.Type.EXACT))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalHeaders() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new StringBody("someBody", Body.Type.EXACT))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new StringBody("someBody", Body.Type.EXACT)).withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalQueryStringParameters() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new StringBody("someBody", Body.Type.EXACT))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new StringBody("someBody", Body.Type.EXACT)).withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenAdditionalBodyParameters() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value"), new Parameter("additionalName", "additionalValue")))));
    }

    @Test
    public void respondWhenHeaderMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withHeaders(new Header("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withHeaders(new Header("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withHeaders(new Header("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.when(httpRequest.withHeaders(new Header("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndExtraHeaders() {
        // when
        mockServerMatcher.when(httpRequest.withHeaders(new Header("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withHeaders(new Header("nameExtra", "valueExtra"), new Header("name", "value"), new Header("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndPathDifferent() {
        // when
        mockServerMatcher.when(httpRequest.withHeaders(new Header("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenQueryStringMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameter(new Parameter("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParametersMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenQueryStringWithMultipleValuesMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void respondWhenBodyParametersWithMultipleValuesMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1", "value2")))));
    }

    @Test
    public void doNotRespondWhenQueryStringWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenBodyParametersWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1", "value3")))));
    }

    @Test
    public void doNotRespondWhenQueryStringWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1"))));
    }

    @Test
    public void doNotRespondWhenBodyParametersWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void respondWhenQueryStringMatchesAndExtraParameters() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameter(new Parameter(".*name", "value.*"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(
                new Parameter("nameExtra", "valueExtra"),                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        )));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndExtraParameters() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter(".*name", "value.*")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameExtra", "valueExtra"),
                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        ))));
    }

    @Test
    public void respondWhenQueryStringParametersMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("name", "val[a-z]{2}"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenQueryStringParametersWithMultipleValuesMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))));
    }

    @Test
    public void doNotRespondWhenQueryStringParametersWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne", "valueThree"))));
    }

    @Test
    public void doNotRespondWhenQueryStringParametersWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne"))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndExtraQueryStringParameters() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("name", "val[a-z]{2}"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(
                new Parameter("nameExtra", "valueExtra"),
                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        )));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndExtraBodyParameters() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "val[a-z]{2}")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameExtra", "valueExtra"),
                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        ))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndMultipleQueryStringParameters() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndMultipleBodyParameters() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("nameOne", "valueTwo", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatchAndMultipleQueryStringParameters() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("nameOne", "valueTwo"), new Parameter("nameTwo", "valueOne"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatchAndMultipleBodyParameters() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueTwo"),
                new Parameter("nameTwo", "valueOne")
        ))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndPathDifferent() {
        // when
        mockServerMatcher.when(httpRequest.withQueryStringParameters(new Parameter("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndPathDifferent() {
        // when
        mockServerMatcher.when(httpRequest.withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenCookieMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withCookies(new Cookie("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void respondWhenCookieWithMultipleValuesMatchesExactly() {
        // when
        mockServerMatcher.when(httpRequest.withCookies(new Cookie("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withCookies(new Cookie("name", "value1", "value2"))));
    }

    @Test
    public void doNotRespondWhenCookieWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.when(httpRequest.withCookies(new Cookie("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withCookies(new Cookie("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenCookieWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.when(httpRequest.withCookies(new Cookie("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertNull(mockServerMatcher.handle(new HttpRequest().withCookies(new Cookie("name", "value1"))));
    }

    @Test
    public void respondWhenCookieMatchesAndExtraCookies() {
        // when
        mockServerMatcher.when(httpRequest.withCookies(new Cookie("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withCookies(new Cookie("nameExtra", "valueExtra"), new Cookie("name", "value"), new Cookie("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenCookieMatchesAndPathDifferent() {
        // when
        mockServerMatcher.when(httpRequest.withCookies(new Cookie("name", "value"))).thenRespond(httpResponse.withBody("someBody"));

        // then
        assertEquals(httpResponse, mockServerMatcher.handle(new HttpRequest().withPath("somePath").withCookies(new Cookie("name", "value"))));
    }
}
