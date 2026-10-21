package org.mockserver.templates.engine.mustache;

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

public class MustacheDateTemplateHelperTest {

    private static final HttpRequest REQUEST = request().withPath("/token").withHeader("Date", "Wed, 21 Oct 2026 15:04:05 GMT");

    @Rule
    public final FixedTime fixedTime = new FixedTime();

    @Mock
    private MockServerLogger mockServerLogger;

    private MustacheTemplateEngine engine;

    @Before
    public void setupTestFixture() {
        openMocks(this);
        when(mockServerLogger.isEnabledForInstance(any(Level.class))).thenReturn(true);
        engine = new MustacheTemplateEngine(mockServerLogger, configuration());
    }

    private String render(String template) {
        return engine.renderTemplate(template, REQUEST);
    }

    @Test
    public void shouldKeepNowBasedOutputsUnchanged() {
        assertThat(render("{{ dates }}|{{ dates.epochSeconds }}"), is(DateTimeFormatter.ISO_INSTANT.format(TimeService.now()) + "|" + TimeService.now().getEpochSecond()));
    }

    @Test
    public void shouldExposeNoArgumentDateMethods() {
        DateTimeFormatter http = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH);
        assertThat(render("{{ dates.now }}"), is(DateTimeFormatter.ISO_INSTANT.format(TimeService.now())));
        assertThat(render("{{ dates.rfc1123 }}"), is(http.format(TimeService.now().atZone(ZoneOffset.UTC))));
        assertThat(render("{{ dates.now.firstDayOfNextMonth.rfc1123 }}"), is(http.format(TimeService.now().atZone(ZoneOffset.UTC).toLocalDate().with(TemporalAdjusters.firstDayOfNextMonth()).atStartOfDay(ZoneOffset.UTC))));
    }

    @Test
    public void shouldReflectRequestDateWithOffsetAsRfc1123() {
        assertThat(render("{{#dates.render}}{{ request.headers.Date.0 }}|offset=1 hours|format=rfc1123{{/dates.render}}"), is("Wed, 21 Oct 2026 16:04:05 GMT"));
    }

    @Test
    public void shouldParseFormatTruncateAndConvertZone() {
        assertThat(render("{{#dates.render}}2026-10-21T15:04:05Z|offset=1 days|format=yyyy-MM-dd{{/dates.render}}"), is("2026-10-22"));
        assertThat(render("{{#dates.render}}21/10/2026 16:04|pattern=dd/MM/yyyy HH:mm|zone=Europe/London{{/dates.render}}"), is("2026-10-21T16:04:00+01:00"));
        assertThat(render("{{#dates.render}}2026-10-21T15:04:05Z|zone=America/New_York|format=HH:mm{{/dates.render}}"), is("11:04"));
        assertThat(render("{{#dates.render}}2026-03-15T10:00:00Z|truncate=firstDayOfMonth|offset=-1 days|format=yyyy-MM-dd{{/dates.render}}"), is("2026-02-28"));
        assertThat(render("{{#dates.render}}2026-03-15T10:00:00Z|offset=1 months|truncate=lastDayOfMonth{{/dates.render}}"), is("2026-04-30T00:00:00Z"));
        assertThat(render("{{#dates.render}}now|format=epochSeconds{{/dates.render}}"), is(String.valueOf(TimeService.now().getEpochSecond())));
    }

    @Test
    public void shouldReportInvalidOptionAsTemplateFailure() {
        String template = "{ 'statusCode': 200, 'body': '{{#dates.render}}now|shift=1 days{{/dates.render}}' }";
        RuntimeException exception = assertThrows(RuntimeException.class, () -> engine.executeTemplate(template, REQUEST, HttpResponseDTO.class));
        assertThat(exception.getMessage(), allOf(containsString("dates.render: unknown option \"shift=1 days\""), containsString("transforming template")));
        RuntimeException badDate = assertThrows(RuntimeException.class, () -> engine.renderTemplate("{{#dates.render}}yesterday{{/dates.render}}", REQUEST));
        assertThat(badDate.getMessage(), containsString("dates.parse: cannot read \"yesterday\" as a date"));
    }

    @Test
    public void shouldRenderDocumentedExample() {
        // the example on the response templates page
        String template = "{ 'statusCode': 200, 'headers': { 'Expires': [ '{{#dates.render}}now|offset=1 hours|format=rfc1123{{/dates.render}}' ], 'Last-Modified': [ '{{ dates.now.startOfDay.rfc1123 }}' ] }, 'body': 'billing period ends {{#dates.render}}now|zone=Europe/London|truncate=lastDayOfMonth|format=d MMMM yyyy{{/dates.render}}' }";
        HttpResponse response = engine.executeTemplate(template, REQUEST, HttpResponseDTO.class);
        DateTimeFormatter http = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.ENGLISH).withZone(ZoneOffset.UTC);
        assertThat(response.getFirstHeader("Expires"), is(http.format(TimeService.now().plus(1, ChronoUnit.HOURS))));
        assertThat(response.getFirstHeader("Last-Modified"), is(http.format(TimeService.now().truncatedTo(ChronoUnit.DAYS))));
        assertThat(response.getBodyAsString(), is("billing period ends " + DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH).format(TimeService.now().atZone(ZoneId.of("Europe/London")).with(TemporalAdjusters.lastDayOfMonth()))));
    }

    @Test
    public void shouldNotLetRequestDataAddRenderOptions() {
        HttpRequest injected = request().withHeader("Date", "2026-10-21T15:04:05Z|offset=1 days");
        RuntimeException exception = assertThrows(RuntimeException.class, () -> engine.renderTemplate("{{#dates.render}}{{ request.headers.Date.0 }}|format=yyyy-MM-dd{{/dates.render}}", injected));
        assertThat(exception.getMessage(), containsString("dates.render: a value inside the section contains |"));
    }

    @Test
    public void shouldFailWhenTheDateIsMissing() {
        RuntimeException exception = assertThrows(RuntimeException.class, () -> engine.renderTemplate("{{#dates.render}}{{ request.headers.Expires.0 }}|offset=1 hours{{/dates.render}}", REQUEST));
        assertThat(exception.getMessage(), containsString("dates.parse: no date given (the value is empty)"));
    }
}
