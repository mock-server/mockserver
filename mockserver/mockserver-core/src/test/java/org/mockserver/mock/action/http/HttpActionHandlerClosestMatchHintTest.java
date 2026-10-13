package org.mockserver.mock.action.http;

import org.junit.*;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockserver.configuration.Configuration;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.mock.crud.CrudDispatcher;
import org.mockserver.model.*;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.curl.HttpRequestToCurlSerializer;
import org.mockserver.time.FixedTime;
import org.mockserver.uuid.UUIDService;
import org.slf4j.event.Level;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * Tests for the closestMatchHintEnabled feature, which attaches a single concise
 * {@code x-mockserver-closest-match-hint} header to genuine data-plane no-match 404s.
 * Verifies:
 * (a) default OFF: the unmatched 404 is unchanged (no hint header);
 * (b) ON with a near-miss expectation: the header names the closest expectation id and first differing field;
 * (c) ON with no expectations: no header (the 404 is left untouched);
 * (d) explicitly OFF: no header even when a diff is available;
 * (e) the hint is header-only (it never sets a response body).
 */
public class HttpActionHandlerClosestMatchHintTest {

    private static Scheduler scheduler;
    @Mock
    private HttpResponseActionHandler mockHttpResponseActionHandler;
    @Mock
    private HttpResponseTemplateActionHandler mockHttpResponseTemplateActionHandler;
    @Mock
    private HttpResponseClassCallbackActionHandler mockHttpResponseClassCallbackActionHandler;
    @Mock
    private HttpResponseObjectCallbackActionHandler mockHttpResponseObjectCallbackActionHandler;
    @Mock
    private HttpForwardActionHandler mockHttpForwardActionHandler;
    @Mock
    private HttpForwardTemplateActionHandler mockHttpForwardTemplateActionHandler;
    @Mock
    private HttpForwardClassCallbackActionHandler mockHttpForwardClassCallbackActionHandler;
    @Mock
    private HttpForwardObjectCallbackActionHandler mockHttpForwardObjectCallbackActionHandler;
    @Mock
    private HttpOverrideForwardedRequestActionHandler mockHttpOverrideForwardedRequestActionHandler;
    @Mock
    private HttpErrorActionHandler mockHttpErrorActionHandler;
    @Mock
    private ResponseWriter mockResponseWriter;
    @Mock
    private MockServerLogger mockServerLogger;
    @Spy
    private HttpRequestToCurlSerializer httpRequestToCurlSerializer = new HttpRequestToCurlSerializer(mockServerLogger);
    @Mock
    private NettyHttpClient mockNettyHttpClient;
    private HttpState mockHttpStateHandler;
    @InjectMocks
    private HttpActionHandler actionHandler;
    private Configuration configuration;

    private static final String HINT_HEADER = "x-mockserver-closest-match-hint";

    @ClassRule
    public static final FixedTime fixedTime = new FixedTime();

    @AfterClass
    public static void stopScheduler() {
        scheduler.shutdown();
    }

    @Before
    public void setupMocks() {
        configuration = configuration().logLevel(Level.INFO);

        mockHttpStateHandler = mock(HttpState.class);
        scheduler = spy(new Scheduler(configuration, mockServerLogger));
        when(mockHttpStateHandler.getScheduler()).thenReturn(scheduler);
        when(mockHttpStateHandler.getUniqueLoopPreventionHeaderValue()).thenReturn("MockServer_" + UUIDService.getUUID());
        when(mockHttpStateHandler.getUniqueLoopPreventionHeaderName()).thenReturn("x-forwarded-by");
        when(mockHttpStateHandler.getCrudDispatcher()).thenReturn(new CrudDispatcher());
        actionHandler = new HttpActionHandler(configuration, null, mockHttpStateHandler, null, null);

        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
    }

    private RequestMatchers.ClosestMatchHint hint(String expectationId, Map<MatchDifference.Field, List<String>> differences) {
        return new RequestMatchers.ClosestMatchHint(expectationId, differences);
    }

    private HttpResponse capturedNotFound(HttpRequest request) {
        ArgumentCaptor<HttpResponse> responseCaptor = ArgumentCaptor.forClass(HttpResponse.class);
        verify(mockResponseWriter).writeResponse(eq(request), responseCaptor.capture(), eq(false));
        return responseCaptor.getValue();
    }

    @Test
    public void defaultOn_nearMiss_headerNamesExpectationAndField() {
        // given - default config (closestMatchHintEnabled = true), the flag is NOT set explicitly
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(hint("EXPECT-DEFAULT", diff));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - the hint header is present by default (no opt-in required)
        HttpResponse writtenResponse = capturedNotFound(request);
        assertThat(writtenResponse.getStatusCode(), is(404));
        assertThat(writtenResponse.getFirstHeader(HINT_HEADER), containsString("EXPECT-DEFAULT"));
        assertThat(writtenResponse.getFirstHeader(HINT_HEADER), containsString("method"));
    }

    @Test
    public void defaultOn_noClosestMatch_noHeader() {
        // given - default config, but nothing came close (mocked state returns null)
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(null);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - the 404 is left untouched (no header, no body)
        HttpResponse writtenResponse = capturedNotFound(request);
        assertThat(writtenResponse.getStatusCode(), is(404));
        assertThat(writtenResponse.getFirstHeader(HINT_HEADER), is(emptyOrNullString()));
        assertThat(writtenResponse.getBodyAsString(), is(emptyOrNullString()));
    }

    @Test
    public void whenEnabled_nearMiss_headerNamesExpectationAndField() {
        // given
        configuration.closestMatchHintEnabled(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(hint("EXPECT-123", diff));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - header names the closest expectation, the first differing field and the reason
        HttpResponse writtenResponse = capturedNotFound(request);
        assertThat(writtenResponse.getStatusCode(), is(404));
        String header = writtenResponse.getFirstHeader(HINT_HEADER);
        assertThat(header, containsString("EXPECT-123"));
        assertThat(header, containsString("method"));
        assertThat(header, containsString("expected POST but was GET"));
        // header-only: the 404 must not gain a body
        assertThat(writtenResponse.getBodyAsString(), is(emptyOrNullString()));
    }

    @Test
    public void whenEnabled_multipleDifferences_reportsFirstFieldAndExtraCount() {
        // given
        configuration.closestMatchHintEnabled(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.PATH, List.of("expected /api/users but was /some_path"));
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        diff.put(MatchDifference.Field.HEADERS, List.of("missing header Content-Type"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(hint("E1", diff));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - first field named, remaining fields summarised as a count (compact, not a dump)
        HttpResponse writtenResponse = capturedNotFound(request);
        String header = writtenResponse.getFirstHeader(HINT_HEADER);
        assertThat(header, containsString("path"));
        assertThat(header, containsString("+2 more field(s) differ"));
        // it must stay compact — should not enumerate every reason string
        assertThat(header, not(containsString("missing header Content-Type")));
    }

    @Test
    public void whenEnabled_longReason_headerIsLengthBounded() {
        // given - a deliberately huge reason string
        configuration.closestMatchHintEnabled(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            huge.append('x');
        }
        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.BODY, List.of(huge.toString()));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(hint("E1", diff));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - the header is bounded (never dumps the full body), and still header-only
        HttpResponse writtenResponse = capturedNotFound(request);
        String header = writtenResponse.getFirstHeader(HINT_HEADER);
        assertThat(header.length() <= 256, is(true));
        assertThat(header, containsString("..."));
        assertThat(writtenResponse.getBodyAsString(), is(emptyOrNullString()));
    }

    @Test
    public void truncatedScan_headerSaysHowManyExpectationsWereChecked_evenWhenTheReasonIsLong() {
        // given - a capped scan with a reason long enough to hit the length bound
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.PATH, List.of("y".repeat(2000)));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(new RequestMatchers.ClosestMatchHint("E1", diff, 1000, true));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - the note is kept whole at the end and the header stays within its bound
        String header = capturedNotFound(request).getFirstHeader(HINT_HEADER);
        assertThat(header, startsWith("expectation E1: path did not match (yyy"));
        assertThat(header, endsWith("...; only the first 1000 expectations were checked"));
        assertThat(header.length(), is(256));
    }

    @Test
    public void truncatedScan_diagnosticBodyAndDebugLogSayHowManyExpectationsWereChecked() {
        // given - every closest-match consumer active and a capped scan
        configuration.attachMismatchDiagnosticToResponse(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);
        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(new RequestMatchers.ClosestMatchHint("E1", diff, 1000, true));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        assertThat(capturedNotFound(request).getBodyAsString(), containsString("\"expectationsChecked\" : 1000"));
        ArgumentCaptor<LogEntry> logEntries = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeastOnce()).logEvent(logEntries.capture());
        assertThat(logEntries.getAllValues().stream().anyMatch(entry -> entry.getMessageFormat() != null
            && entry.getMessageFormat().startsWith("closest match diff for unmatched request, among the first {} expectations only")), is(true));
    }

    @Test
    public void untruncatedScan_diagnosticBodyAndDebugLogHaveNoNote() {
        // given
        configuration.attachMismatchDiagnosticToResponse(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);
        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(new RequestMatchers.ClosestMatchHint("E1", diff, 3, false));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        assertThat(capturedNotFound(request).getBodyAsString(), not(containsString("truncated")));
        ArgumentCaptor<LogEntry> logEntries = ArgumentCaptor.forClass(LogEntry.class);
        verify(mockServerLogger, atLeastOnce()).logEvent(logEntries.capture());
        assertThat(logEntries.getAllValues().stream().anyMatch(entry -> "closest match diff for unmatched request:{}".equals(entry.getMessageFormat())), is(true));
    }

    @Test
    public void untruncatedScan_headerHasNoNote() {
        // given
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(new RequestMatchers.ClosestMatchHint("E1", diff, 3, false));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then
        assertThat(capturedNotFound(request).getFirstHeader(HINT_HEADER), is("expectation E1: method did not match (expected POST but was GET)"));
    }

    @Test
    public void whenEnabled_noExpectations_noHeader() {
        // given - enabled but nothing came close (no expectations configured)
        configuration.closestMatchHintEnabled(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(null);

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - the 404 is left untouched (no hint header, no body)
        HttpResponse writtenResponse = capturedNotFound(request);
        assertThat(writtenResponse.getStatusCode(), is(404));
        assertThat(writtenResponse.getFirstHeader(HINT_HEADER), is(emptyOrNullString()));
        assertThat(writtenResponse.getBodyAsString(), is(emptyOrNullString()));
    }

    @Test
    public void allConsumersActive_closestMatchComputedExactlyOnce() {
        // given - all three closest-match consumers active: DEBUG diff log (detailedVerificationFailures
        // default true + DEBUG enabled by the mock logger), verbose diagnostic, and the hint header
        configuration.attachMismatchDiagnosticToResponse(true);
        configuration.closestMatchHintEnabled(true);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(hint("E1", diff));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - the matcher scan runs ONCE, not once per consumer (COR-01: no triple scan)
        verify(mockHttpStateHandler, times(1)).findClosestMatchHint(request);
        // and all consumers still fired off the single result: hint header + verbose header + JSON body
        HttpResponse writtenResponse = capturedNotFound(request);
        assertThat(writtenResponse.getFirstHeader(HINT_HEADER), containsString("E1"));
        assertThat(writtenResponse.getFirstHeader("x-mockserver-closest-match"), containsString("method"));
        assertThat(writtenResponse.getBodyAsString(), containsString("\"differences\""));
    }

    @Test
    public void whenDisabledExplicitly_noHeaderEvenWithDiff() {
        // given - hint explicitly disabled; the closest match may still be computed for other
        // consumers (e.g. the DEBUG diff log), but no hint header must be added
        configuration.closestMatchHintEnabled(false);
        HttpRequest request = request("some_path");
        when(mockHttpStateHandler.firstMatchingExpectation(request)).thenReturn(null);

        Map<MatchDifference.Field, List<String>> diff = new LinkedHashMap<>();
        diff.put(MatchDifference.Field.METHOD, List.of("expected POST but was GET"));
        lenient().when(mockHttpStateHandler.findClosestMatchHint(request)).thenReturn(hint("E1", diff));

        // when
        actionHandler.processAction(request, mockResponseWriter, null, new HashSet<>(), false, true);

        // then - no hint header is added when the flag is off
        HttpResponse writtenResponse = capturedNotFound(request);
        assertThat(writtenResponse.getStatusCode(), is(404));
        assertThat(writtenResponse.getFirstHeader(HINT_HEADER), is(emptyOrNullString()));
    }
}
