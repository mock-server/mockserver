package org.mockserver.templates.engine.helpers;

import org.junit.Test;
import org.mockserver.time.TimeService;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class DateTemplateHelperTest {

    private final DateTemplateHelper helper = new DateTemplateHelper();

    @Test
    public void shouldFormatWithPattern() {
        String result = helper.format("yyyy-MM-dd");
        assertThat(result, matchesPattern("\\d{4}-\\d{2}-\\d{2}"));
    }

    @Test
    public void shouldPlusSeconds() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusSeconds(60);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 60)));
    }

    @Test
    public void shouldPlusMinutes() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusMinutes(5);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 300)));
    }

    @Test
    public void shouldPlusHours() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusHours(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 3600)));
    }

    @Test
    public void shouldPlusDays() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.plusDays(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(greaterThanOrEqualTo(beforeEpoch + 86400)));
    }

    @Test
    public void shouldMinusSeconds() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusSeconds(60);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 60)));
    }

    @Test
    public void shouldMinusMinutes() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusMinutes(5);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 300)));
    }

    @Test
    public void shouldMinusHours() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusHours(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 3600)));
    }

    @Test
    public void shouldMinusDays() {
        long beforeEpoch = TimeService.now().getEpochSecond();
        String result = helper.minusDays(1);
        Instant parsed = Instant.from(DateTimeFormatter.ISO_INSTANT.parse(result));
        assertThat(parsed.getEpochSecond(), is(lessThanOrEqualTo(beforeEpoch - 86400)));
    }

    @Test
    public void shouldReturnEpochSeconds() {
        long before = TimeService.now().getEpochSecond();
        long result = helper.epochSeconds();
        assertThat(result, is(greaterThanOrEqualTo(before)));
    }

    @Test
    public void shouldReturnEpochMillis() {
        long before = TimeService.now().toEpochMilli();
        long result = helper.epochMillis();
        assertThat(result, is(greaterThanOrEqualTo(before)));
    }

    @Test
    public void shouldReturnEpochSecondsPlus() {
        long before = TimeService.now().getEpochSecond();
        String result = helper.epochSecondsPlus(60);
        long resultEpoch = Long.parseLong(result);
        assertThat(resultEpoch, is(greaterThanOrEqualTo(before + 60)));
    }

    @Test
    public void shouldReturnEpochSecondsMinus() {
        long before = TimeService.now().getEpochSecond();
        String result = helper.epochSecondsMinus(60);
        long resultEpoch = Long.parseLong(result);
        assertThat(resultEpoch, is(lessThanOrEqualTo(before - 60)));
    }

    @Test
    public void shouldReturnIsoInstantFromToString() {
        String result = helper.toString();
        assertThat(result, matchesPattern("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.*Z"));
    }

    @Test
    public void shouldBeRegisteredInTemplateFunctions() {
        Object datesHelper = org.mockserver.templates.engine.TemplateFunctions.BUILT_IN_HELPERS.get("dates");
        assertThat(datesHelper, is(notNullValue()));
        assertThat(datesHelper, instanceOf(DateTemplateHelper.class));
    }
}
