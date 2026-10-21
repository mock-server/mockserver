package org.mockserver.templates.engine.velocity;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
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
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.openMocks;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

public class VelocityDateTemplateHelperTest {

    private static final HttpRequest REQUEST = request().withPath("/token").withHeader("Date", "Wed, 21 Oct 2026 15:04:05 GMT");

    @Rule
    public final FixedTime fixedTime = new FixedTime();

    @Mock
    private MockServerLogger mockServerLogger;

    private VelocityTemplateEngine engine;

    @Before
    public void setupTestFixture() {
        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        engine = new VelocityTemplateEngine(mockServerLogger, configuration());
    }

    private String render(String template) {
        return engine.renderTemplate(template, REQUEST, null);
    }

    @Test
    public void shouldKeepNowBasedMethodsUnchanged() {
        assertThat(render("$dates|$dates.plusHours(1)|$dates.epochSeconds()"), is(
            DateTimeFormatter.ISO_INSTANT.format(TimeService.now()) + "|"
                + DateTimeFormatter.ISO_INSTANT.format(TimeService.now().plus(1, ChronoUnit.HOURS)) + "|"
                + TimeService.now().getEpochSecond()
        ));
    }

    @Test
    public void shouldUseServerClockForTimezoneAwareNow() {
        assertThat(render("$dates.now()"), is(DateTimeFormatter.ISO_INSTANT.format(TimeService.now())));
        assertThat(render("$dates.now('Europe/London').format('HH:mm')"), is(DateTimeFormatter.ofPattern("HH:mm").format(TimeService.now().atZone(ZoneId.of("Europe/London")))));
    }

    @Test
    public void shouldReflectRequestDateWithOffsetAsRfc1123() {
        assertThat(render("$dates.rfc1123($dates.offset($request.headers.Date[0], '1 hours'))"), is("Wed, 21 Oct 2026 16:04:05 GMT"));
    }

    @Test
    public void shouldParseFormatTruncateAndChain() {
        assertThat(render("$dates.parse('21/10/2026', 'dd/MM/yyyy').plusDays(1).format('yyyy-MM-dd')"), is("2026-10-22"));
        assertThat(render("$dates.format('2026-10-21T15:04:05Z', 'HH:mm', 'Europe/London')"), is("16:04"));
        assertThat(render("$dates.truncate('2026-10-21T15:04:05Z', 'firstDayOfNextMonth')"), is("2026-11-01T00:00:00Z"));
        assertThat(render("$dates.parse('2026-03-15T10:00:00Z').firstDayOfMonth().minusDays(1).format('yyyy-MM-dd')"), is("2026-02-28"));
        assertThat(render("$dates.parse(1792595045).rfc1123()"), is("Wed, 21 Oct 2026 15:04:05 GMT"));
        assertThat(render("#if($dates.parse($request.headers.Date[0]).isBefore('2027-01-01'))before#{else}after#end"), is("before"));
    }

    @Test
    public void shouldReportInvalidDateAsTemplateFailure() {
        String template = "{ 'statusCode': 200, 'body': '$dates.parse(\"yesterday\")' }";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> engine.executeTemplate(template, REQUEST, HttpResponseDTO.class));
        assertThat(exception.getMessage(), allOf(containsString("dates.parse: cannot read \"yesterday\" as a date"), containsString("transforming template")));
    }

    @Test
    public void shouldRenderDocumentedExample() {
        // the example on the response templates page
        String template = "{ 'statusCode': 200, 'headers': { 'Expires': [ '$dates.now().plusHours(1).rfc1123()' ], 'Last-Modified': [ '$dates.now().startOfDay().rfc1123()' ] }, 'body': 'billing period ends $dates.now('Europe/London').lastDayOfMonth().format('d MMMM yyyy')' }";
        HttpResponse response = engine.executeTemplate(template, REQUEST, HttpResponseDTO.class);
        DateTimeFormatter http = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
        assertThat(response.getFirstHeader("Expires"), is(http.format(TimeService.now().plus(1, ChronoUnit.HOURS))));
        assertThat(response.getFirstHeader("Last-Modified"), is(http.format(TimeService.now().truncatedTo(ChronoUnit.DAYS))));
        assertThat(response.getBodyAsString(), is("billing period ends " + DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(TimeService.now().atZone(ZoneId.of("Europe/London")).with(TemporalAdjusters.lastDayOfMonth()))));
    }
}
