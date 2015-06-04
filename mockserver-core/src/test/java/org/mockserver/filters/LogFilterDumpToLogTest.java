package org.mockserver.filters;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
import org.mockserver.client.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.slf4j.Logger;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

/**
 * @author jamesdbloom
 */
public class LogFilterDumpToLogTest {

    private final HttpResponse httpResponseOne = new HttpResponse().withBody("body_one");
    private final HttpResponse httpResponseTwo = new HttpResponse().withBody("body_two");
    private final HttpResponse httpResponseThree = new HttpResponse().withBody("body_three");
    private final HttpRequest httpRequest = new HttpRequest().withPath("some_path");
    private final HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
    @Mock
    private Logger requestLogger;
    @InjectMocks
    private LogFilter logFilter;

    @Before
    public void prepareTestFixture() {
        logFilter = new LogFilter();
        initMocks(this);

        // given
        logFilter.onResponse(httpRequest, httpResponseOne);
        logFilter.onResponse(otherHttpRequest, httpResponseTwo);
        logFilter.onResponse(httpRequest, httpResponseThree);
    }

    @Test
    public void shouldDumpAllToLogAsJSONForNull() {
        // when
        logFilter.dumpToLog(null, false);

        // then
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseOne)));
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseThree)));
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(otherHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(requestLogger);
    }


    @Test
    public void shouldDumpAllToLogAsJavaForNull() {
        // when
        logFilter.dumpToLog(null, true);

        // then
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseOne)));
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseThree)));
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(otherHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(requestLogger);
    }

    @Test
    public void shouldDumpAllToLogAsJSONIfMatchAll() {
        // when
        logFilter.dumpToLog(new HttpRequest(), false);

        // then
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseOne)));
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseThree)));
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(otherHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(requestLogger);
    }

    @Test
    public void shouldDumpAllToLogAsJavaIfMatchAll() {
        // when
        logFilter.dumpToLog(new HttpRequest(), true);

        // then
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseOne)));
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseThree)));
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(otherHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(requestLogger);
    }

    @Test
    public void shouldDumpOnlyMatchingToLogAsJSON() {
        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_path"), false);

        // then
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseOne)));
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseThree)));
        verifyNoMoreInteractions(requestLogger);

        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_other_path"), false);

        // then
        verify(requestLogger).warn(new ExpectationSerializer().serialize(new Expectation(otherHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(requestLogger);
    }

    @Test
    public void shouldDumpOnlyMatchingToLogAsJava() {
        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_path"), true);

        // then
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseOne)));
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(httpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseThree)));
        verifyNoMoreInteractions(requestLogger);

        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_other_path"), true);

        // then
        verify(requestLogger).warn(new ExpectationToJavaSerializer().serializeAsJava(0, new Expectation(otherHttpRequest, Times.once(), TimeToLive.unlimited()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(requestLogger);
    }
}
