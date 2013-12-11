package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;

import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class MockServerDumpTest {

    @Mock
    private Logger logger;
    @InjectMocks
    private MockServer mockServer;

    @Before
    public void prepareTestFixture() {
        mockServer = new MockServer();
        initMocks(this);
    }

    @Test
    public void shouldClearAllExpectations() {
        // given
        mockServer
                .when(
                        new HttpRequest()
                                .withPath("some_path"))
                .respond(
                        new HttpResponse()
                                .withBody("some_response_body")
                );
        mockServer
                .when(
                        new HttpRequest()
                                .withPath("some_other_path"))
                .respond(
                        new HttpResponse()
                                .withBody("some_other_response_body")
                );

        // when
        mockServer.dumpToLog();

        // then
        verify(logger).warn("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"\",\n" +
                "    \"path\" : \"some_path\",\n" +
                "    \"body\" : \"\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"parameters\" : [ ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_response_body\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 0\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 1,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}");
        verify(logger).warn("{\n" +
                "  \"httpRequest\" : {\n" +
                "    \"method\" : \"\",\n" +
                "    \"path\" : \"some_other_path\",\n" +
                "    \"body\" : \"\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"parameters\" : [ ]\n" +
                "  },\n" +
                "  \"httpResponse\" : {\n" +
                "    \"statusCode\" : 200,\n" +
                "    \"body\" : \"some_other_response_body\",\n" +
                "    \"cookies\" : [ ],\n" +
                "    \"headers\" : [ ],\n" +
                "    \"delay\" : {\n" +
                "      \"timeUnit\" : \"MICROSECONDS\",\n" +
                "      \"value\" : 0\n" +
                "    }\n" +
                "  },\n" +
                "  \"times\" : {\n" +
                "    \"remainingTimes\" : 1,\n" +
                "    \"unlimited\" : true\n" +
                "  }\n" +
                "}");
    }

}
