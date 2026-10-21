package org.mockserver.templates.engine.javascript;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.time.FixedTime;
import org.mockserver.time.TimeService;
import org.slf4j.event.Level;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Locale;


import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class JavaScriptDateTemplateHelperTest {

    private static final Configuration CONFIGURATION = configuration().javascriptTemplateExecutionTimeout(0L);
    private static final HttpRequest REQUEST = request().withPath("/token").withHeader("Date", "Wed, 21 Oct 2026 15:04:05 GMT");

    @Rule
    public final FixedTime fixedTime = new FixedTime();

    @Mock
    private MockServerLogger mockServerLogger;

    @Before
    public void setupTestFixture() {
        assumeTrue("GraalVM Polyglot API available", JavaScriptTemplateEngine.isPolyglotAvailable());
        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
    }

    private String body(String expression) {
        return new JavaScriptTemplateEngine(mockServerLogger, CONFIGURATION)
            .executeTemplate("return { 'statusCode': 200, 'body': " + expression + " };", REQUEST, HttpResponseDTO.class)
            .getBodyAsString();
    }

    @Test
    public void shouldKeepNowBasedMethodsUnchanged() {
        assertThat(body("dates.plusHours(1) + '|' + dates.format('yyyy')"), is(
            DateTimeFormatter.ISO_INSTANT.format(TimeService.now().plus(1, ChronoUnit.HOURS)) + "|" + DateTimeFormatter.ofPattern("yyyy").format(TimeService.now().atZone(ZoneId.of("UTC")))
        ));
    }

    @Test
    public void shouldUseServerClockForTimezoneAwareNow() {
        assertThat(body("dates.now().toString()"), is(DateTimeFormatter.ISO_INSTANT.format(TimeService.now())));
        assertThat(body("dates.now('Asia/Tokyo').format('yyyy-MM-dd HH:mm')"), is(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(TimeService.now().atZone(ZoneId.of("Asia/Tokyo")))));
    }

    @Test
    public void shouldReflectRequestDateWithOffsetAsRfc1123() {
        assertThat(body("dates.rfc1123(dates.offset(request.headers.Date[0], '1 hours'))"), is("Wed, 21 Oct 2026 16:04:05 GMT"));
    }

    @Test
    public void shouldParseFormatTruncateAndChain() {
        assertThat(body("dates.parse('21/10/2026', 'dd/MM/yyyy').plusDays(1).format('yyyy-MM-dd')"), is("2026-10-22"));
        assertThat(body("dates.format('2026-10-21T15:04:05Z', 'HH:mm', 'Europe/London')"), is("16:04"));
        assertThat(body("dates.truncate('2026-10-21T15:04:05Z', 'first day of next year').toString()"), is("2027-01-01T00:00:00Z"));
        assertThat(body("dates.parse(1792595045).rfc1123() + '|' + dates.parse(1792595045000).toString()"), is("Wed, 21 Oct 2026 15:04:05 GMT|2026-10-21T15:04:05Z"));
        assertThat(body("String(dates.epochSeconds('2026-10-21T15:04:05Z'))"), is("1792595045"));
    }

    @Test
    public void shouldReportInvalidOffsetAsTemplateFailure() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> body("dates.offset('2026-10-21T15:04:05Z', 'tomorrow').toString()"));
        assertThat(exception.getMessage(), containsString("dates: invalid date offset \"tomorrow\""));
    }

    @Test
    public void shouldRenderDocumentedExample() {
        // the example on the response templates page
        String template = "return { 'statusCode': 200, 'headers': { 'Expires': [ dates.now().plusHours(1).rfc1123() ], 'Last-Modified': [ dates.now().startOfDay().rfc1123() ] }, 'body': 'billing period ends ' + dates.now('Europe/London').lastDayOfMonth().format('d MMMM yyyy') };";
        HttpResponse response = new JavaScriptTemplateEngine(mockServerLogger, CONFIGURATION).executeTemplate(template, REQUEST, HttpResponseDTO.class);
        DateTimeFormatter http = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
        assertThat(response.getFirstHeader("Expires"), is(http.format(TimeService.now().plus(1, ChronoUnit.HOURS))));
        assertThat(response.getFirstHeader("Last-Modified"), is(http.format(TimeService.now().truncatedTo(ChronoUnit.DAYS))));
        assertThat(response.getBodyAsString(), is("billing period ends " + DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(TimeService.now().atZone(ZoneId.of("Europe/London")).with(TemporalAdjusters.lastDayOfMonth()))));
    }
}
