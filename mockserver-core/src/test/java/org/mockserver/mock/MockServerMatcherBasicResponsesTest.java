package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.scheduler.Scheduler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.ui.MockServerMatcherNotifier.Cause.*;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherBasicResponsesTest {

    private MockServerMatcher mockServerMatcher;
    private Session mockServerSession;

    @Before
    public void prepareTestFixture() {
        MockServerLogger mockLogFormatter = mock(MockServerLogger.class);
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerSession = new Session();
        mockServerMatcher = new MockServerMatcher(mockLogFormatter, scheduler, webSocketClientRegistry, mockServerSession);
    }

    @Test
    public void respondWhenPathMatches() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath")).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void respondWhenRegexPathMatches() {
        // when
        Expectation expectation = new Expectation(request().withPath("[a-zA-Z]*")).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void doNotRespondWhenPathDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withPath("somePath")).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")));
    }

    @Test
    public void doNotRespondWhenRegexPathDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withPath("[a-z]*")).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath123")));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalHeaders() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath")).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath")).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenPathMatchesAndAdditionalBodyParameters() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath")).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenBodyMatches() {
        // when
        Expectation expectation = new Expectation(request().withBody(new StringBody("someBody"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody"))));
    }

    @Test
    public void respondWhenRegexBodyMatches() {
        // when
        Expectation expectation = new Expectation(request().withBody(new RegexBody("[a-zA-Z]*"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody"))));
    }

    @Test
    public void doNotRespondWhenBodyDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withBody(new StringBody("someBody"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someOtherBody"))));
    }

    @Test
    public void doNotRespondWhenRegexBodyDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withBody(new RegexBody("[a-z]*"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someOtherBody123"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalHeaders() {
        // when
        Expectation expectation = new Expectation(request().withBody(new StringBody("someBody"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody")).withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenBodyMatchesAndAdditionalQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(request().withBody(new StringBody("someBody"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new StringBody("someBody")).withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenAdditionalBodyParameters() {
        // when
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value"), new Parameter("additionalName", "additionalValue")))));
    }

    @Test
    public void respondWhenHeaderMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withHeaders(new Header("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("name", "value"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withHeaders(new Header("name", "value1", "value2"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenHeaderWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(request().withHeaders(new Header("name", "value1", "value2"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("name", "value1"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndExtraHeaders() {
        // when
        Expectation expectation = new Expectation(request().withHeaders(new Header("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withHeaders(new Header("nameExtra", "valueExtra"), new Header("name", "value"), new Header("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenHeaderMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(request().withHeaders(new Header("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withHeaders(new Header("name", "value"))));
    }

    @Test
    public void respondWhenQueryStringMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withQueryStringParameter(new Parameter("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParametersMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenQueryStringWithMultipleValuesMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1", "value2"))));
    }

    @Test
    public void respondWhenBodyParametersWithMultipleValuesMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1", "value2")))));
    }

    @Test
    public void doNotRespondWhenQueryStringWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1", "value3"))));
    }

    @Test
    public void doNotRespondWhenBodyParametersWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1", "value3")))));
    }

    @Test
    public void doNotRespondWhenQueryStringWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(request().withQueryStringParameter(new Parameter("name", "value1", "value2"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameter(new Parameter("name", "value1"))));
    }

    @Test
    public void doNotRespondWhenBodyParametersWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(request().withBody(new ParameterBody(new Parameter("name", "value1", "value2")))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(new Parameter("name", "value1")))));
    }

    @Test
    public void respondWhenQueryStringMatchesAndExtraParameters() {
        // when
        Expectation expectation = new Expectation(request().withQueryStringParameter(new Parameter(".*name", "value.*"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameExtra", "valueExtra"), new Parameter("name", "value"),
                new Parameter("nameExtraExtra", "valueExtraExtra")
        )));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndExtraParameters() {
        // when
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter(".*name", "value.*")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

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
        Expectation expectation = new Expectation(request().withQueryStringParameters(new Parameter("name", "val[a-z]{2}"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenQueryStringParametersWithMultipleValuesMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))));
    }

    @Test
    public void doNotRespondWhenQueryStringParametersWithMultipleValuesDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne", "valueThree"))));
    }

    @Test
    public void doNotRespondWhenQueryStringParametersWithMultipleValuesHasMissingValue() {
        // when
        mockServerMatcher.add(new Expectation(request().withQueryStringParameters(new Parameter("name", "valueOne", "valueTwo"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(new Parameter("name", "valueOne"))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndExtraQueryStringParameters() {
        // when
        Expectation expectation = new Expectation(request().withQueryStringParameters(new Parameter("name", "val[a-z]{2}"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

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
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter("name", "val[a-z]{2}")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

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
        Expectation expectation = new Expectation(request().withQueryStringParameters(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndMultipleBodyParameters() {
        // when
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        ))));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatch() {
        // when
        mockServerMatcher.add(new Expectation(request().withQueryStringParameters(new Parameter("nameOne", "valueTwo", "valueTwo"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatchAndMultipleQueryStringParameters() {
        // when
        mockServerMatcher.add(new Expectation(request().withQueryStringParameters(new Parameter("nameOne", "valueTwo"), new Parameter("nameTwo", "valueOne"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withQueryStringParameters(
                new Parameter("nameOne", "valueOne"),
                new Parameter("nameTwo", "valueTwo")
        )));
    }

    @Test
    public void doNotRespondWhenQueryStringParameterDoesNotMatchAndMultipleBodyParameters() {
        // when
        mockServerMatcher.add(new Expectation(request().withBody(new ParameterBody(new Parameter("nameOne", "valueOne"), new Parameter("nameTwo", "valueTwo")))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withBody(new ParameterBody(
                new Parameter("nameOne", "valueTwo"),
                new Parameter("nameTwo", "valueOne")
        ))));
    }

    @Test
    public void respondWhenQueryStringParameterMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(request().withQueryStringParameters(new Parameter("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withQueryStringParameters(new Parameter("name", "value"))));
    }

    @Test
    public void respondWhenBodyParameterMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(request().withBody(new ParameterBody(new Parameter("name", "value")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withBody(new ParameterBody(new Parameter("name", "value")))));
    }

    @Test
    public void respondWhenCookieMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withCookies(new Cookie("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("name", "value"))));
    }

    @Test
    public void doNotRespondWhenCookieDoesNotMatchValue() {
        // when
        mockServerMatcher.add(new Expectation(request().withCookies(new Cookie("name", "value1"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("name", "value2"))));
    }

    @Test
    public void doNotRespondWhenCookieDoesNotMatchName() {
        // when
        mockServerMatcher.add(new Expectation(request().withCookies(new Cookie("name1", "value"))).thenRespond(response().withBody("someBody")), API);

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("name2", "value"))));
    }

    @Test
    public void respondWhenCookieMatchesAndExtraCookies() {
        // when
        Expectation expectation = new Expectation(request().withCookies(new Cookie("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withCookies(new Cookie("nameExtra", "valueExtra"), new Cookie("name", "value"), new Cookie("nameExtraExtra", "valueExtraExtra"))));
    }

    @Test
    public void respondWhenCookieMatchesAndPathDifferent() {
        // when
        Expectation expectation = new Expectation(request().withCookies(new Cookie("name", "value"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("somePath").withCookies(new Cookie("name", "value"))));
    }
    
    @Test
    public void respondWhenSessionStateMatchesExactly() {
        // when
        Expectation expectation = new Expectation(request().withSession(new Session().withEntry("key1", "value1"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        
        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }

    @Test
    public void respondWhenSessionMatchesAndExtraEntries() {
     // when
        Expectation expectation = new Expectation(request().withSession(new Session().withEntry("key2", "value2"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        mockServerSession.withEntry("key2", "value2");
        
        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
        
    @Test
    public void doNotRespondWhenSessionEntryDoesNotMatchValue() {
        // when
        Expectation expectation = new Expectation(request().withSession(new Session().withEntry("key1", "value1"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "notValue1");

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void doNotRespondWhenSessionEntryDoesNotMatchName() {
        // when
        Expectation expectation = new Expectation(request().withSession(new Session().withEntry("key1", "value1"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("notKey1", "value1");

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }

    @Test
    public void doNotRespondWhenSessionMissingEntries() {
     // when
        Expectation expectation = new Expectation(request().withSession(new Session()
            .withEntry("key1", "value1")
            .withEntry("key2", "value2")
        )).thenRespond(response().withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        
        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void respondWhenSessionEntryDontMatchNotValue() {
        // when
        Expectation expectation = new Expectation(request().withSession(
            new Session().withEntry(NottableString.string("key1"), NottableString.not("value1")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "notValue1");

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void doNotRespondWhenMissingSessionEntryWithNotValue() {
        // when
        Expectation expectation = new Expectation(request().withSession(
            new Session().withEntry(NottableString.string("key1"), NottableString.not("value1")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key2", "value2");

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void doNotRespondWhenSessionEntryMatchNotValue() {
        // when
        Expectation expectation = new Expectation(request().withSession(
            new Session().withEntry(NottableString.string("key1"), NottableString.not("value1")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }

    @Test
    public void respondWhenSessionEntryDontMatchNotEntryNameWithMatchEntryValue() {
        // when
        Expectation expectation = new Expectation(request().withSession(
            new Session().withEntry(NottableString.not("key1"), NottableString.string("value1")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("notKey1", "value1");

        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void doNotRespondWhenSessionEntryDontMatchNotEntryNameWithNotMatchingEntryValue() {
        // when
        Expectation expectation = new Expectation(request().withSession(
            new Session().withEntry(NottableString.not("key1"), NottableString.string("value1")))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("notKey1", "notValue1");

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
    }
    
    @Test
    public void respondWhenSessionMatchShouldNotModifyRequestObject() {
        // when
        Expectation expectation = new Expectation(request().withSession(new Session().withEntry("key1", "value1"))).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        
        HttpRequest httpRequest = new HttpRequest();
        assertNull(httpRequest.getSession());
        
        // then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(httpRequest));
        assertNull(httpRequest.getSession());
    }
    
    @Test
    public void doNotRespondBasedOnRequestObjectSession() {
        // when
        Expectation expectation = new Expectation(
            request().withSession(
                new Session().withEntry("key1", "value1")
            )
        ).thenRespond(
            response().withBody("someBody")
        );
        
        mockServerMatcher.add(expectation, API);
    
        HttpRequest httpRequest = new HttpRequest().withSession(
            new Session().withEntry("key1", "value1")
        );

        // then
        assertNull(mockServerMatcher.firstMatchingExpectation(httpRequest));
        assertEquals(0, mockServerSession.getMap().size());
        assertEquals(1, httpRequest.getSession().getMap().size());
    }


    @Test
    public void addedExpecationResponseShouldNotAddSessionEntries() {
     // when
        Expectation expectation = new Expectation(request()).thenRespond(response()
            .withSession(new Session().withEntry("key1", "value1"))
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        
        //prior to match - session should remain the same
        assertTrue(mockServerSession.getMap().isEmpty());
    }
    
    @Test
    public void matchedExpecationResponseShouldAddSessionEntry() {
     // when
        Expectation expectation = new Expectation(request()).thenRespond(response()
            .withSession(new Session().withEntry("key1", "value1"))
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        
        //prior to match - session should remain the same
        assertTrue(mockServerSession.getMap().isEmpty());
        
     //then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
    }
 
    @Test
    public void matchedExpecationResponseShouldAddSessionEntryToExistingSession() {
     // when
        Expectation expectation = new Expectation(request()).thenRespond(response()
            .withSession(new Session().withEntry("key2", "value2"))
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        
        //prior to match - session should remain the same
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));

     //then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        assertEquals(2, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        assertEquals(NottableString.string("value2"), mockServerSession.getMap().get(NottableString.string("key2")));
    }
    
    @Test
    public void matchedExpecationResponseShouldReplaceExistingSessionEntryValue() {
     // when
        Expectation expectation = new Expectation(request()).thenRespond(response()
            .withSession(new Session().withEntry("key", "value2"))
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key", "value1");

        //prior to match - session should remain the same
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key")));
        
     //then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value2"), mockServerSession.getMap().get(NottableString.string("key")));
    }
    
    @Test
    public void matchedExpecationWithEmptySessionShouldClearSession() {
     // when
        Expectation expectation = new Expectation(request()).thenRespond(response()
            .withSession(new Session())
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");

        //prior to match - session should remain the same
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        
     //then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        assertTrue(mockServerSession.getMap().isEmpty());
    }
    
    @Test
    public void matchedExpecationShouldRemainUnchaged() {
     // when
        Expectation expectation = new Expectation(request()).thenRespond(response()
            .withSession(new Session().withEntry("key2", "value2"))
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");
        
        //expectation response should remain the same before matched
        assertEquals(1, expectation.getHttpResponse().getSession().getMap().size());
        assertEquals(NottableString.string("value2"), expectation.getHttpResponse().getSession().getMap().get(NottableString.string("key2")));

     //then
        assertEquals(expectation, mockServerMatcher.firstMatchingExpectation(new HttpRequest()));
        assertEquals(1, expectation.getHttpResponse().getSession().getMap().size());
        assertEquals(NottableString.string("value2"), expectation.getHttpResponse().getSession().getMap().get(NottableString.string("key2")));
    }
    
    @Test
    public void unMatchedExpecationResponseShouldNotChangeSession() {
     // when
        Expectation expectation = new Expectation(request().withPath("somePath")).thenRespond(response()
            .withSession(new Session().withEntry("key2", "value2"))
            .withBody("someBody"));
        
        mockServerMatcher.add(expectation, API);
        mockServerSession.withEntry("key1", "value1");

        //prior to match - session should remain the same
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
        
     //then
        assertNull(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("notSomePath")));
        assertEquals(1, mockServerSession.getMap().size());
        assertEquals(NottableString.string("value1"), mockServerSession.getMap().get(NottableString.string("key1")));
    }
 
}
