package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.LoggingFormatter;
import org.mockserver.model.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherBasicResponsesTest {

    private MockServerMatcher mockServerMatcher;
    private HttpRequest httpRequest;
    private HttpResponse httpResponse;
    private LoggingFormatter mockLogFormatter;

    @Before
    public void prepareTestFixture() {
        httpRequest = new HttpRequest();
        httpResponse = new HttpResponse();
        mockLogFormatter = mock(LoggingFormatter.class);
        mockServerMatcher = new MockServerMatcher(mockLogFormatter);
    }

    @Test
    public void respondWhenPathMatches() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void respondWhenRegexPathMatches() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("[a-zA-Z]*")).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doNotRespondWhenPathDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")));
    }

    @Test
    public void doNotRespondWhenRegexPathDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withPath("[a-z]*")).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath123")));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalHeaders() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalBodyParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withPath("somePath")).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenBodyMatches() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new StringBody("someBody"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody"))));
    }

    @Test
    public void respondWhenRegexBodyMatches() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new RegexBody("[a-zA-Z]*"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody"))));
    }

    @Test
    public void doNotRespondWhenBodyDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withBody(new StringBody("someBody"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someOtherBody"))));
    }

    @Test
    public void doNotRespondWhenRegexBodyDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withBody(new RegexBody("[a-z]*"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someOtherBody123"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalHeaders() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new StringBody("someBody"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody")).withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new StringBody("someBody"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody")).withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenAdditionalBodyParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value"), new Parameter("additionalName", "additionalValue")))));
    }

    @Test
    public void respondWhenHeaderMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withHeaders(new Header("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withHeaders(new Header("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withHeaders(new Header("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndExtraHeaders() {
        // when
        Expectation expectation = new Expectation(httpRequest.withHeaders(new Header("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("nameExtra", "valueExtra"), new Header("name", "value"), new Header("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(httpRequest.withHeaders(new Header("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenQueryStringMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameter(new Parameter("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParametersMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenQueryStringWithMultipleValuesMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void respondWhenBodyParametersWithMultipleValuesMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1", "value2")))));
    }

    @Test
    public void doNotRespondWhenQueryStringWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenBodyParametersWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1", "value3")))));
    }

    @Test
    public void doNotRespondWhenQueryStringWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1"))));
    }

    @Test
    public void doNotRespondWhenBodyParametersWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void respondWhenQueryStringMatchesAndExtraParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameter(new Parameter(".*name", "value.*"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameExtra", "valueExtra"), new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        )));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndExtraParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter(".*name", "value.*")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameExtra", "valueExtra"),
                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        ))));
    }

    @Test
    public void respondWhenQueryStringParametersMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameters(new Parameter("name", "val[a-z]{2}"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenQueryStringParametersWithMultipleValuesMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))));
    }

    @Test
    public void doNotRespondWhenQueryStringParametersWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne", "valueThree"))));
    }

    @Test
    public void doNotRespondWhenQueryStringParametersWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne"))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndExtraQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameters(new Parameter("name", "val[a-z]{2}"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameExtra", "valueExtra"),
                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        )));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndExtraBodyParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "val[a-z]{2}")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameExtra", "valueExtra"),
                new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        ))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndMultipleQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameters(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndMultipleBodyParameters() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withQueryStringParameters(new Parameter("nameOne", "valueTwo", "valueTwo"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatchAndMultipleQueryStringParameters() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withQueryStringParameters(new Parameter("nameOne", "valueTwo"), new Parameter("nameTwo", "valueOne"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatchAndMultipleBodyParameters() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo")))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueTwo"),
                new Parameter("nameTwo", "valueOne")
        ))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(httpRequest.withQueryStringParameters(new Parameter("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(httpRequest.withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenCookieMatchesExactly() {
        // when
        Expectation expectation = new Expectation(httpRequest.withCookies(new Cookie("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doNotRespondWhenCookieDoesNotMatchValue() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withCookies(new Cookie("name", "value1"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("name", "value2"))));
    }

    @Test
    public void doNotRespondWhenCookieDoesNotMatchName() {
        // when
        mockServerMatcher.add(new Expectation(httpRequest.withCookies(new Cookie("name1", "value"))).thenRespond(httpResponse.withBody("someBody")));

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("name2", "value"))));
    }

    @Test
    public void respondWhenCookieMatchesAndExtraCookies() {
        // when
        Expectation expectation = new Expectation(httpRequest.withCookies(new Cookie("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("nameExtra", "valueExtra"), new Cookie("name", "value"), new Cookie("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenCookieMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(httpRequest.withCookies(new Cookie("name", "value"))).thenRespond(httpResponse.withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withCookies(new Cookie("name", "value"))));
    }
}
