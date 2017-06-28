package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherDumpTest {

    @Mock
    private Logger requestLogger;
    @InjectMocks
    private MockServerMatcher mockServerMatcher;

    @Before
    public void prepareTestFixture() {
        mockServerMatcher = new MockServerMatcher();
        initMocks(this);
    }

    @Test
    public void shouldWriteAllExpectationsToTheLog() {
        // given
        mockServerMatcher
                .when(
                        new HttpRequest()
                                .withPath("some_path")
                )
                .thenRespond(
                        new HttpResponse()
                                .withBody("some_response_body")
                );
        mockServerMatcher
                .when(
                        new HttpRequest()
                                .withPath("some_other_path")
                )
                .thenRespond(
                        new HttpResponse()
                                .withBody("some_other_response_body")
                );

        // when
        mockServerMatcher.dumpToLog(null);

        // then
        verify(requestLogger).warn("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"some_path\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some_response_body\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 0," + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");
        verify(requestLogger).warn("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"some_other_path\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some_other_response_body\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 0," + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");
    }

    @Test
    public void shouldWriteOnlyMatchingExpectationsToTheLog() {
        // given
        mockServerMatcher
                .when(
                        new HttpRequest()
                                .withPath("some_path")
                )
                .thenRespond(
                        new HttpResponse()
                                .withBody("some_response_body")
                );
        mockServerMatcher
                .when(
                        new HttpRequest()
                                .withPath("some_other_path")
                )
                .thenRespond(
                        new HttpResponse()
                                .withBody("some_other_response_body")
                );

        // when
        mockServerMatcher.dumpToLog(new HttpRequest().withPath("some_path"));

        // then
        verify(requestLogger).warn("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"some_path\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"body\" : \"some_response_body\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 0," + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"timeToLive\" : {" + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");
        verifyNoMoreInteractions(requestLogger);
    }


    @Test
    public void shouldCorrectlyMatchRegexForResponsesWithStatusCode() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"some_path\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 200," + NEW_LINE +
                "    \"body\" : \"" + Base64Converter.bytesToBase64String("some_response_body".getBytes()) + "\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 0," + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");

        // then
        assertThat(result, is("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"path\" : \"some_path\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 200," + NEW_LINE +
                "    \"body\" : \"some_response_body\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 0," + NEW_LINE +
                "    \"unlimited\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchRegexForResponsesWithDelay() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"" + Base64Converter.bytesToBase64String("someBody".getBytes()) + "\"," + NEW_LINE +
                "        \"delay\": {" + NEW_LINE +
                "            \"timeUnit\": null," + NEW_LINE +
                "            \"value\": null" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }" + NEW_LINE +
                "}");

        // then
        assertThat(result, is("{" + NEW_LINE +
                "    \"httpRequest\": {" + NEW_LINE +
                "        \"path\": \"somePath\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"httpResponse\": {" + NEW_LINE +
                "        \"body\": \"someBody\"," + NEW_LINE +
                "        \"delay\": {" + NEW_LINE +
                "            \"timeUnit\": null," + NEW_LINE +
                "            \"value\": null" + NEW_LINE +
                "        }" + NEW_LINE +
                "    }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchRegexForComplexResponses() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"url\" : \"http://www.example.com\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"value\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"body\" : \"" + Base64Converter.bytesToBase64String("someBody".getBytes()) + "\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");

        // then
        assertThat(result, is("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"url\" : \"http://www.example.com\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"value\" : \"someBody\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"body\" : \"someBody\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchNotRegexForComplex() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"url\" : \"http://www.example.com\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"value\" : \"some_body\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"body\" : \"some_body\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}");

        // then
        assertThat(result, is("{" + NEW_LINE +
                "  \"httpRequest\" : {" + NEW_LINE +
                "    \"method\" : \"someMethod\"," + NEW_LINE +
                "    \"url\" : \"http://www.example.com\"," + NEW_LINE +
                "    \"path\" : \"somePath\"," + NEW_LINE +
                "    \"queryStringParameters\" : [ {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameOne\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + NEW_LINE +
                "    }, {" + NEW_LINE +
                "      \"name\" : \"queryStringParameterNameTwo\"," + NEW_LINE +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"body\" : {" + NEW_LINE +
                "      \"type\" : \"STRING\"," + NEW_LINE +
                "      \"value\" : \"some_body\"" + NEW_LINE +
                "    }," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"httpResponse\" : {" + NEW_LINE +
                "    \"statusCode\" : 304," + NEW_LINE +
                "    \"body\" : \"some_body\"," + NEW_LINE +
                "    \"cookies\" : [ {" + NEW_LINE +
                "      \"name\" : \"someCookieName\"," + NEW_LINE +
                "      \"values\" : [ \"someCookieValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"headers\" : [ {" + NEW_LINE +
                "      \"name\" : \"someHeaderName\"," + NEW_LINE +
                "      \"values\" : [ \"someHeaderValue\" ]" + NEW_LINE +
                "    } ]," + NEW_LINE +
                "    \"delay\" : {" + NEW_LINE +
                "      \"timeUnit\" : \"MICROSECONDS\"," + NEW_LINE +
                "      \"value\" : 1" + NEW_LINE +
                "    }" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"times\" : {" + NEW_LINE +
                "    \"remainingTimes\" : 5," + NEW_LINE +
                "    \"unlimited\" : false" + NEW_LINE +
                "  }" + NEW_LINE +
                "}"));
    }
}
