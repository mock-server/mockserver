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
        verify(requestLogger).warn("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 200," + System.getProperty("line.separator") +
                "    \"body\" : \"some_response_body\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 0," + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");
        verify(requestLogger).warn("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_other_path\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 200," + System.getProperty("line.separator") +
                "    \"body\" : \"some_other_response_body\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 0," + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
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
        verify(requestLogger).warn("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 200," + System.getProperty("line.separator") +
                "    \"body\" : \"some_response_body\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 0," + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");
        verifyNoMoreInteractions(requestLogger);
    }


    @Test
    public void shouldCorrectlyMatchRegexForResponsesWithStatusCode() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 200," + System.getProperty("line.separator") +
                "    \"body\" : \"" + Base64Converter.stringToBase64Bytes("some_response_body".getBytes()) + "\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 0," + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // then
        assertThat(result, is("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"path\" : \"some_path\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 200," + System.getProperty("line.separator") +
                "    \"body\" : \"some_response_body\"" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 0," + System.getProperty("line.separator") +
                "    \"unlimited\" : true" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchRegexForResponsesWithDelay() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"" + Base64Converter.stringToBase64Bytes("someBody".getBytes()) + "\"," + System.getProperty("line.separator") +
                "        \"delay\": {" + System.getProperty("line.separator") +
                "            \"timeUnit\": null," + System.getProperty("line.separator") +
                "            \"value\": null" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}");

        // then
        assertThat(result, is("{" + System.getProperty("line.separator") +
                "    \"httpRequest\": {" + System.getProperty("line.separator") +
                "        \"path\": \"somePath\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"httpResponse\": {" + System.getProperty("line.separator") +
                "        \"body\": \"someBody\"," + System.getProperty("line.separator") +
                "        \"delay\": {" + System.getProperty("line.separator") +
                "            \"timeUnit\": null," + System.getProperty("line.separator") +
                "            \"value\": null" + System.getProperty("line.separator") +
                "        }" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchRegexForComplexResponses() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"" + Base64Converter.stringToBase64Bytes("someBody".getBytes()) + "\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // then
        assertThat(result, is("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"someBody\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"someBody\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchNotRegexForComplex() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"some_body\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}");

        // then
        assertThat(result, is("{" + System.getProperty("line.separator") +
                "  \"httpRequest\" : {" + System.getProperty("line.separator") +
                "    \"method\" : \"someMethod\"," + System.getProperty("line.separator") +
                "    \"url\" : \"http://www.example.com\"," + System.getProperty("line.separator") +
                "    \"path\" : \"somePath\"," + System.getProperty("line.separator") +
                "    \"queryStringParameters\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameOne\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]" + System.getProperty("line.separator") +
                "    }, {" + System.getProperty("line.separator") +
                "      \"name\" : \"queryStringParameterNameTwo\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"body\" : {" + System.getProperty("line.separator") +
                "      \"type\" : \"EXACT\"," + System.getProperty("line.separator") +
                "      \"value\" : \"some_body\"" + System.getProperty("line.separator") +
                "    }," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"httpResponse\" : {" + System.getProperty("line.separator") +
                "    \"statusCode\" : 304," + System.getProperty("line.separator") +
                "    \"body\" : \"some_body\"," + System.getProperty("line.separator") +
                "    \"cookies\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someCookieName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someCookieValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"headers\" : [ {" + System.getProperty("line.separator") +
                "      \"name\" : \"someHeaderName\"," + System.getProperty("line.separator") +
                "      \"values\" : [ \"someHeaderValue\" ]" + System.getProperty("line.separator") +
                "    } ]," + System.getProperty("line.separator") +
                "    \"delay\" : {" + System.getProperty("line.separator") +
                "      \"timeUnit\" : \"MICROSECONDS\"," + System.getProperty("line.separator") +
                "      \"value\" : 1" + System.getProperty("line.separator") +
                "    }" + System.getProperty("line.separator") +
                "  }," + System.getProperty("line.separator") +
                "  \"times\" : {" + System.getProperty("line.separator") +
                "    \"remainingTimes\" : 5," + System.getProperty("line.separator") +
                "    \"unlimited\" : false" + System.getProperty("line.separator") +
                "  }" + System.getProperty("line.separator") +
                "}"));
    }
}
