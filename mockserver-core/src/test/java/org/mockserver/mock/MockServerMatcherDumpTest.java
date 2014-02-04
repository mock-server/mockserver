package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;

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
                                .withPath("some_path"))
                .thenRespond(
                        new HttpResponse()
                                .withBody("some_response_body")
                );
        mockServerMatcher
                .when(
                        new HttpRequest()
                                .withPath("some_other_path"))
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
                                .withPath("some_path"))
                .thenRespond(
                        new HttpResponse()
                                .withBody("some_response_body")
                );
        mockServerMatcher
                .when(
                        new HttpRequest()
                                .withPath("some_other_path"))
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

}
