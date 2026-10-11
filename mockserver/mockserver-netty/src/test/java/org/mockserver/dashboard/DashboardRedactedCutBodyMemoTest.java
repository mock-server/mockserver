package org.mockserver.dashboard;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.dashboard.model.DashboardBodyCap;
import org.mockserver.log.model.LogEntry;
import org.mockserver.mock.Expectation;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.dashboard.DashboardCutBodyMemoTest.CountingJsonBody;
import static org.mockserver.dashboard.DashboardCutBodyMemoTest.corpus;
import static org.mockserver.dashboard.DashboardCutBodyMemoTest.json;
import static org.mockserver.dashboard.DashboardCutBodyMemoTest.jsonArray;
import static org.mockserver.dashboard.DashboardCutBodyMemoTest.text;
import static org.mockserver.dashboard.DashboardCutBodyMemoTest.update;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * With {@code redactSecretsInLog} on, a large body's REDACTED cut text is kept, so it is decoded and redacted once
 * rather than at every quote in every update. A kept cut must be shown only under the redaction settings (and the
 * message Content-Type) it was redacted under: never across a toggle of redaction or a change of the masked fields.
 */
public class DashboardRedactedCutBodyMemoTest {

    private static final int CAP = DashboardBodyCap.MAX_BODY_CHARACTERS;
    private static final long EPOCH_TIME = 1_700_000_000_000L;
    private static final String BODY_SECRET = "hunter2-body-secret";
    private static final String HEADER_SECRET = "header-secret-value";
    private static final String COOKIE_SECRET = "cookie-secret-value";
    private static final String QUERY_SECRET = "query-secret-value";
    private static final String SET_COOKIE_SECRET = "set-cookie-secret-value";

    private static Configuration redacting(String bodyFields) {
        return configuration().redactSecretsInLog(true).fixtureBodyRedactFields(bodyFields);
    }

    // Newest first, as the dashboard walks the log, with credentials in every part redaction masks.
    private static List<LogEntry> exchange(String path, String contentType, BodyWithContentType<?> requestBody, BodyWithContentType<?> responseBody) {
        HttpRequest request = request(path).withMethod("POST")
            .withHeader("Authorization", "Bearer " + HEADER_SECRET)
            .withCookie("session", COOKIE_SECRET)
            .withQueryStringParameter("api_key", QUERY_SECRET)
            .withBody(requestBody);
        if (contentType != null) {
            request.withHeader("Content-Type", contentType);
        }
        HttpResponse response = response().withStatusCode(200)
            .withHeader("Set-Cookie", "session=" + SET_COOKIE_SECRET)
            .withBody(responseBody);
        if (contentType != null) {
            response.withHeader("Content-Type", contentType);
        }
        Expectation expectation = new Expectation(request(path)).withId("expectation" + path).thenRespond(response().withBody("{\"ok\":true}"));
        return Arrays.asList(
            new LogEntry().setEpochTime(EPOCH_TIME).setType(EXPECTATION_RESPONSE).setCorrelationId(path)
                .setHttpRequest(request).setHttpResponse(response)
                .setMessageFormat("returning response:{}for request:{}").setArguments(response, request),
            new LogEntry().setEpochTime(EPOCH_TIME).setType(EXPECTATION_MATCHED).setCorrelationId(path)
                .setHttpRequest(request).setExpectation(expectation)
                .setMessageFormat("request:{}matched expectation:{}").setArguments(request, expectation),
            new LogEntry().setEpochTime(EPOCH_TIME).setType(RECEIVED_REQUEST).setCorrelationId(path)
                .setHttpRequest(request)
                .setMessageFormat("received request:{}").setArguments(request)
        );
    }

    private static String secretJson(int minimumLength) {
        return "{\"password\":\"" + BODY_SECRET + "\",\"items\":" + jsonArray("item", minimumLength) + "}";
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    // Bodies that a body-field redaction changes, or replaces whole, each over the cap unless noted.
    private static List<Supplier<BodyWithContentType<?>>> secretCorpus() {
        StringBuilder secretArray = new StringBuilder("[");
        for (int i = 0; secretArray.length() < 2 * CAP; i++) {
            secretArray.append(i > 0 ? "," : "").append("{\"id\":").append(i).append(",\"password\":\"").append(BODY_SECRET).append(i).append("\"}");
        }
        secretArray.append(']');
        StringBuilder sse = new StringBuilder();
        for (int i = 0; sse.length() < 2 * CAP; i++) {
            sse.append("event: chunk\r\ndata: {\"n\":").append(i).append(",\"password\":\"").append(BODY_SECRET).append("\"}\r\n\r\n");
        }
        String secretAfterCut = "{\"items\":" + jsonArray("item", 2 * CAP) + ",\"password\":\"" + BODY_SECRET + "\"}";
        String pretty = secretJson(3 * CAP / 2).replace(",", ",\n    ").replace("{", "{\n  ");
        return Arrays.asList(
            () -> json(secretJson(3 * CAP)),
            () -> json(secretArray.toString()),
            () -> json(secretAfterCut),
            () -> json(pretty),
            () -> text(secretJson(2 * CAP)),
            () -> text(sse.toString()),
            // unparseable and naming the field: replaced whole by a short placeholder, so never cut
            () -> json("{\"password\":\"" + BODY_SECRET + "\", \"unterminated\": \"" + repeat("abcé", CAP)),
            () -> text("password: " + BODY_SECRET + "\n" + repeat("plain text line\n", CAP / 8)),
            () -> new BinaryBody(utf8(secretJson(2 * CAP))),
            () -> json(secretJson(100))
        );
    }

    private static List<Supplier<BodyWithContentType<?>>> everyBody() {
        List<Supplier<BodyWithContentType<?>>> bodies = new ArrayList<>(corpus());
        bodies.addAll(secretCorpus());
        return bodies;
    }

    private static void assertNoSecretShown(String update, String description) {
        for (String secret : Arrays.asList(BODY_SECRET, HEADER_SECRET, COOKIE_SECRET, QUERY_SECRET, SET_COOKIE_SECRET)) {
            assertThat(description + " shows " + secret, update, not(containsString(secret)));
        }
    }

    @Test
    public void shouldDecodeEachLargeBodyOnceAcrossRowsAndUpdatesWithRedactionOn() throws Exception {
        for (String bodyFields : Arrays.asList("", "password")) {
            CountingJsonBody requestBody = new CountingJsonBody(secretJson(3 * CAP));
            CountingJsonBody responseBody = new CountingJsonBody(secretJson(3 * CAP));
            List<LogEntry> reverse = exchange("/decode-once", "application/json", requestBody, responseBody);

            String first = update(reverse, redacting(bodyFields));
            int requestDecodesInFirst = requestBody.decodes.get();
            int responseDecodesInFirst = responseBody.decodes.get();
            String second = update(reverse, redacting(bodyFields));

            // the request is shown six times per update and the response twice; a kept cut is a soft reference, so
            // allow one decode more should a collection clear it in between
            assertThat("fields [" + bodyFields + "] request decodes", requestBody.decodes.get() - requestDecodesInFirst, lessThanOrEqualTo(1));
            assertThat("fields [" + bodyFields + "] response decodes", responseBody.decodes.get() - responseDecodesInFirst, lessThanOrEqualTo(1));
            assertThat(second, is(first));
        }
    }

    @Test
    public void shouldShowFromAKeptRedactedCutExactlyWhatAFreshRedactedRenderShows() throws Exception {
        List<Supplier<BodyWithContentType<?>>> bodies = everyBody();
        for (String bodyFields : Arrays.asList("", "password", "password,name")) {
            for (String contentType : Arrays.asList(null, "application/json")) {
                for (int i = 0; i < bodies.size(); i++) {
                    Supplier<BodyWithContentType<?>> body = bodies.get(i);
                    String description = "fields [" + bodyFields + "], Content-Type " + contentType + ", body " + i;
                    List<LogEntry> retained = exchange("/corpus-" + i, contentType, body.get(), body.get());
                    // the same bodies, never shown before, so nothing is kept for them
                    String fresh = update(exchange("/corpus-" + i, contentType, body.get(), body.get()), redacting(bodyFields));

                    assertThat(description + ", first update", update(retained, redacting(bodyFields)), is(fresh));
                    assertThat(description + ", later update", update(retained, redacting(bodyFields)), is(fresh));
                }
            }
        }
    }

    @Test
    public void shouldNeverShowACutKeptUnderOtherRedactionSettings() throws Exception {
        List<Configuration> sequence = Arrays.asList(
            configuration(), redacting("password"), configuration(), redacting("password"),
            redacting(""), redacting("password"), redacting("token"), redacting("password,token"),
            configuration(), redacting("password")
        );
        List<Supplier<BodyWithContentType<?>>> bodies = secretCorpus();
        for (int i = 0; i < bodies.size(); i++) {
            Supplier<BodyWithContentType<?>> body = bodies.get(i);
            List<LogEntry> retained = exchange("/toggle-" + i, null, body.get(), body.get());
            for (int step = 0; step < sequence.size(); step++) {
                Configuration configuration = sequence.get(step);
                String description = "body " + i + ", step " + step;
                String shown = update(retained, configuration);

                assertThat(description, shown, is(update(exchange("/toggle-" + i, null, body.get(), body.get()), configuration)));
                if (configuration.redactSecretsInLog()) {
                    if (configuration.fixtureBodyRedactFields().contains("password")) {
                        assertNoSecretShown(shown, description);
                    } else {
                        assertThat(description, shown, not(containsString(HEADER_SECRET)));
                    }
                }
            }
        }
    }

    // A received request whose message also quotes the body's secret as free text, which only the values found in the
    // body scrub: a search kept under other settings would leave it shown.
    private static List<LogEntry> quotingTheSecret(String path, String contentType, BodyWithContentType<?> body) {
        HttpRequest request = request(path).withMethod("POST").withBody(body);
        if (contentType != null) {
            request.withHeader("Content-Type", contentType);
        }
        return Collections.singletonList(new LogEntry().setEpochTime(EPOCH_TIME).setType(RECEIVED_REQUEST).setCorrelationId(path)
            .setHttpRequest(request)
            .setMessageFormat("received request:{}noted:{}").setArguments(request, "the password sent was " + BODY_SECRET));
    }

    @Test
    public void shouldKeepScrubbingFreeTextThatQuotesABodySecret() throws Exception {
        List<Configuration> sequence = Arrays.asList(
            redacting("password"), redacting("token"), redacting("password"), configuration(), redacting("password,token"), redacting("password")
        );
        for (String contentType : Arrays.asList(null, "application/octet-stream")) {
            BinaryBody shared = new BinaryBody(utf8(secretJson(2 * CAP)));
            List<LogEntry> retained = quotingTheSecret("/quoted", contentType, shared);
            List<LogEntry> otherContentType = quotingTheSecret("/quoted", contentType == null ? "application/octet-stream" : null, shared);
            for (int step = 0; step < sequence.size(); step++) {
                Configuration configuration = sequence.get(step);
                String description = "Content-Type " + contentType + ", step " + step;
                String shown = update(retained, configuration);
                String otherShown = update(otherContentType, configuration);

                assertThat(description, shown, is(update(quotingTheSecret("/quoted", contentType, new BinaryBody(utf8(secretJson(2 * CAP)))), configuration)));
                assertThat(description + ", other Content-Type", otherShown, is(update(quotingTheSecret("/quoted", contentType == null ? "application/octet-stream" : null, new BinaryBody(utf8(secretJson(2 * CAP)))), configuration)));
                if (configuration.redactSecretsInLog() && configuration.fixtureBodyRedactFields().contains("password")) {
                    // with no Content-Type the body is read as JSON, so its password is found and scrubbed from the text
                    assertThat(description, (contentType == null ? shown : otherShown), not(containsString(BODY_SECRET)));
                }
            }
        }
    }

    @Test
    public void shouldKeepABodyQuotedUnderTwoContentTypesApart() throws Exception {
        // one retained body in two requests (or two responses): with no Content-Type a binary body is redacted as
        // UTF-8 JSON, with one it is shown as base64, which redaction cannot read; neither may stand for the other
        for (String sharedBy : Arrays.asList("request", "response")) {
            for (boolean octetStreamFirst : Arrays.asList(true, false)) {
                BinaryBody shared = new BinaryBody(utf8(secretJson(2 * CAP)));
                Supplier<BodyWithContentType<?>> own = () -> new BinaryBody(utf8(secretJson(2 * CAP)));
                Supplier<BodyWithContentType<?>> requestBody = "request".equals(sharedBy) ? () -> shared : own;
                Supplier<BodyWithContentType<?>> responseBody = "response".equals(sharedBy) ? () -> shared : own;
                List<LogEntry> untyped = exchange("/untyped", null, requestBody.get(), responseBody.get());
                List<LogEntry> octetStream = exchange("/octet-stream", "application/octet-stream", requestBody.get(), responseBody.get());
                String freshUntyped = update(exchange("/untyped", null, own.get(), own.get()), redacting("password"));
                String freshOctetStream = update(exchange("/octet-stream", "application/octet-stream", own.get(), own.get()), redacting("password"));

                for (int round = 0; round < 2; round++) {
                    String description = "shared by " + sharedBy + ", octet-stream first " + octetStreamFirst + ", round " + round;
                    String untypedShown;
                    String octetStreamShown;
                    if (octetStreamFirst) {
                        octetStreamShown = update(octetStream, redacting("password"));
                        untypedShown = update(untyped, redacting("password"));
                    } else {
                        untypedShown = update(untyped, redacting("password"));
                        octetStreamShown = update(octetStream, redacting("password"));
                    }
                    assertThat(description, untypedShown, is(freshUntyped));
                    assertThat(description, octetStreamShown, is(freshOctetStream));
                    assertThat(description, untypedShown, not(containsString(BODY_SECRET)));
                }
            }
        }
    }
}
