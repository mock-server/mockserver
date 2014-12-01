package org.mockserver.filters;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockserver.client.serialization.ExpectationSerializer;
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
    private Logger logger;
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
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne)));
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)));
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(logger);
    }


    @Test
    public void shouldDumpAllToLogAsJavaForNull() {
        // when
        logFilter.dumpToLog(null, true);

        // then
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne)));
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)));
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldDumpAllToLogAsJSONIfMatchAll() {
        // when
        logFilter.dumpToLog(new HttpRequest(), false);

        // then
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne)));
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)));
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldDumpAllToLogAsJavaIfMatchAll() {
        // when
        logFilter.dumpToLog(new HttpRequest(), true);

        // then
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne)));
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)));
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldDumpOnlyMatchingToLogAsJSON() {
        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_path"), false);

        // then
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne)));
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)));
        verifyNoMoreInteractions(logger);

        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_other_path"), false);

        // then
        verify(logger).warn(new ExpectationSerializer().serialize(new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void shouldDumpOnlyMatchingToLogAsJava() {
        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_path"), true);

        // then
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseOne)));
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(httpRequest, Times.once()).thenRespond(httpResponseThree)));
        verifyNoMoreInteractions(logger);

        // when
        logFilter.dumpToLog(new HttpRequest().withPath("some_other_path"), true);

        // then
        verify(logger).warn(new ExpectationSerializer().serializeAsJava(new Expectation(otherHttpRequest, Times.once()).thenRespond(httpResponseTwo)));
        verifyNoMoreInteractions(logger);
    }
}
