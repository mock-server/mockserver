package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.Base64Converter;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;

import static com.google.common.base.Charsets.UTF_8;
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
        mockServerMatcher.dumpToLog(null, false);

        // then
        verify(requestLogger).info("{" + NEW_LINE +
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
        verify(requestLogger).info("{" + NEW_LINE +
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
    public void shouldWriteAllExpectationsToTheLogInJava() {
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
        mockServerMatcher.dumpToLog(null, false);

        // then
        verify(requestLogger).info("{" + NEW_LINE +
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
        verify(requestLogger).info("{" + NEW_LINE +
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
        mockServerMatcher.dumpToLog(new HttpRequest().withPath("some_path"), false);

        // then
        verify(requestLogger).info("{" + NEW_LINE +
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
    public void shouldWriteOnlyMatchingExpectationsToTheLogInJava() {
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
        mockServerMatcher.dumpToLog(new HttpRequest().withPath("some_path"), false);

        // then
        verify(requestLogger).info("{" + NEW_LINE +
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
}
