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
        verify(requestLogger).warn("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"some_path\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_response_body\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 0,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}");
        verify(requestLogger).warn("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"some_other_path\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_other_response_body\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 0,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
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
        verify(requestLogger).warn("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"some_path\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_response_body\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 0,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}");
        verifyNoMoreInteractions(requestLogger);
    }


    @Test
    public void shouldCorrectlyMatchRegexForResponsesWithStatusCode() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"some_path\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"" + Base64Converter.printBase64Binary("some_response_body".getBytes()) + "\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 0,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}");

        // then
        assertThat(result, is("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"path\" : \"some_path\"\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_response_body\"\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 0,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchRegexForResponsesWithDelay() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"" + Base64Converter.printBase64Binary("someBody".getBytes()) + "\",\n" +
                "        \"delay\": {\n" +
                "            \"timeUnit\": null,\n" +
                "            \"value\": null\n" +
                "        }\n" +
                "    }\n" +
                "}");

        // then
        assertThat(result, is("{\n" +
                "    \"httpRequest\": {\n" +
                "        \"path\": \"somePath\"\n" +
                "    },\n" +
                "    \"httpResponse\": {\n" +
                "        \"body\": \"someBody\",\n" +
                "        \"delay\": {\n" +
                "            \"timeUnit\": null,\n" +
                "            \"value\": null\n" +
                "        }\n" +
                "    }\n" +
                "}"));
    }

    @Test
    public void shouldCorrectlyMatchRegexForComplexResponses() {
        // when
        String result = mockServerMatcher.cleanBase64Response("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryStringParameters\" : [ {\n" +
                "      \"name\" : \"queryStringParameterNameOne\",\n" +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]\n" +
                "    }, {\n" +
                "      \"name\" : \"queryStringParameterNameTwo\",\n" +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]\n" +
                "    } ],\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"EXACT\",\n" +
                "      \"value\" : \"someBody\"\n" +
                "    },\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 304,\n" +
                "    \"body\" : \"" + Base64Converter.printBase64Binary("someBody".getBytes()) + "\",\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 1\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 5,\n" +
                "    \"unlimited\" : false\n" +
                "  }\n" +
                "}");

        // then
        assertThat(result, is("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"someMethod\",\n" +
                "    \"url\" : \"http://www.example.com\",\n" +
                "    \"path\" : \"somePath\",\n" +
                "    \"queryStringParameters\" : [ {\n" +
                "      \"name\" : \"queryStringParameterNameOne\",\n" +
                "      \"values\" : [ \"queryStringParameterValueOne_One\", \"queryStringParameterValueOne_Two\" ]\n" +
                "    }, {\n" +
                "      \"name\" : \"queryStringParameterNameTwo\",\n" +
                "      \"values\" : [ \"queryStringParameterValueTwo_One\" ]\n" +
                "    } ],\n" +
                "    \"body\" : {\n" +
                "      \"type\" : \"EXACT\",\n" +
                "      \"value\" : \"someBody\"\n" +
                "    },\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 304,\n" +
                "    \"body\" : \"someBody\",\n" +
                "    \"cookies\" : [ {\n" +
                "      \"name\" : \"someCookieName\",\n" +
                "      \"values\" : [ \"someCookieValue\" ]\n" +
                "    } ],\n" +
                "    \"headers\" : [ {\n" +
                "      \"name\" : \"someHeaderName\",\n" +
                "      \"values\" : [ \"someHeaderValue\" ]\n" +
                "    } ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 1\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 5,\n" +
                "    \"unlimited\" : false\n" +
                "  }\n" +
                "}"));
    }
}
