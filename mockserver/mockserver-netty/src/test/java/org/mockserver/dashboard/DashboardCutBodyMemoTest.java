package org.mockserver.dashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.dashboard.model.DashboardBodyCap;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.dashboard.serializers.DashboardLogEntryDTOGroupSerializer;
import org.mockserver.dashboard.serializers.DashboardLogEntryDTOSerializer;
import org.mockserver.dashboard.serializers.DescriptionProcessor;
import org.mockserver.dashboard.serializers.DescriptionSerializer;
import org.mockserver.dashboard.serializers.ThrowableSerializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.matchers.MatchType;
import org.mockserver.mock.Expectation;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;
import org.mockserver.model.XmlBody;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.apache.commons.lang3.StringUtils.repeat;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Every dashboard update decoded (and, for JSON, parsed) each large body at every place the log quotes it, only
 * to cut it to {@link DashboardBodyCap#MAX_BODY_CHARACTERS}. A retained body's cut text is now kept, so each body is
 * decoded once; the update a kept cut produces must be the one a fresh decode produces.
 */
public class DashboardCutBodyMemoTest {

    private static final int CAP = DashboardBodyCap.MAX_BODY_CHARACTERS;
    private static final long EPOCH_TIME = 1_700_000_000_000L;
    private static final ObjectMapper MAPPER = ObjectMapperFactory.createObjectMapper(
        new DashboardLogEntryDTOSerializer(),
        new DashboardLogEntryDTOGroupSerializer(),
        new DescriptionSerializer(),
        new ThrowableSerializer()
    );

    // A JSON body held as a retained entry holds it (bytes only), counting every time its text is decoded.
    private static final class CountingJsonBody extends JsonBody {
        private final AtomicInteger decodes = new AtomicInteger();

        private CountingJsonBody(String json) {
            super(json, json.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON_UTF_8, MatchType.ONLY_MATCHING_FIELDS);
            releaseDerivedForms();
        }

        @Override
        public String getValue() {
            decodes.incrementAndGet();
            return super.getValue();
        }

        @Override
        public String getValueWithoutCaching() {
            decodes.incrementAndGet();
            return super.getValueWithoutCaching();
        }
    }

    private static String jsonArray(String label, int minimumLength) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; json.length() < minimumLength; i++) {
            json.append(i > 0 ? "," : "").append("{\"id\":").append(i).append(",\"name\":\"").append(label).append("-café-").append(i).append("\"}");
        }
        return json.append(']').toString();
    }

    // Newest first, as the dashboard walks the log: one exchange's response, match and received request entries.
    private static List<LogEntry> exchange(String path, BodyWithContentType<?> requestBody, BodyWithContentType<?> responseBody) {
        HttpRequest request = request(path).withMethod("POST").withBody(requestBody);
        HttpResponse response = response().withStatusCode(200).withBody(responseBody);
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

    // The log sections of one update over these entries, as the dashboard serialises them.
    private static String update(List<LogEntry> reverse, Configuration configuration) throws Exception {
        List<DashboardLogEntryDTO> dtos = new ArrayList<>();
        for (LogEntry logEntry : reverse) {
            dtos.add(new DashboardLogEntryDTO(logEntry, configuration));
        }
        List<Object> logMessages = new LinkedList<>();
        List<Map<String, Object>> recordedRequests = new LinkedList<>();
        List<Map<String, Object>> proxiedRequests = new LinkedList<>();
        DashboardWebSocketHandler.populateLogSections(
            dtos.stream(), true, 100, new DashboardWebSocketHandler.FrameBudget(DashboardWebSocketHandler.MAX_UPDATE_CHARACTERS),
            logMessages, recordedRequests, proxiedRequests,
            new DescriptionProcessor(configuration), new DescriptionProcessor(configuration), new DescriptionProcessor(configuration));
        Map<String, Object> sections = new LinkedHashMap<>();
        sections.put("logMessages", logMessages);
        sections.put("recordedRequests", recordedRequests);
        sections.put("proxiedRequests", proxiedRequests);
        // entry ids differ between two builds of the same exchange; everything else must not
        String text = MAPPER.writeValueAsString(sections);
        for (LogEntry logEntry : reverse) {
            text = text.replace(logEntry.id(), "<id>");
        }
        return text;
    }

    @Test
    public void shouldDecodeEachLargeBodyOnceAcrossRowsAndUpdates() throws Exception {
        CountingJsonBody requestBody = new CountingJsonBody(jsonArray("request", 3 * CAP));
        CountingJsonBody responseBody = new CountingJsonBody(jsonArray("response", 3 * CAP));
        List<LogEntry> reverse = exchange("/decode-once", requestBody, responseBody);

        String first = update(reverse, configuration());
        String second = update(reverse, configuration());

        // decoded each time without the cache: the request is quoted six times per update, the response twice;
        // a kept cut is a soft reference, so allow one decode more should a collection clear it in between
        assertThat(requestBody.decodes.get(), lessThanOrEqualTo(2));
        assertThat(responseBody.decodes.get(), lessThanOrEqualTo(2));
        assertThat(second, is(first));
    }

    @Test
    public void shouldDecodeBodiesThatFitEveryTime() throws Exception {
        CountingJsonBody requestBody = new CountingJsonBody(jsonArray("request", CAP / 2));
        CountingJsonBody responseBody = new CountingJsonBody(jsonArray("response", CAP / 2));
        List<LogEntry> reverse = exchange("/fits", requestBody, responseBody);

        String first = update(reverse, configuration());
        String second = update(reverse, configuration());

        // only a cut is kept: a body shown whole is decoded wherever it is shown
        assertThat(requestBody.decodes.get(), is(12));
        assertThat(responseBody.decodes.get(), is(4));
        assertThat(second, is(first));
        assertThat(first, containsString("request-café-0"));
    }

    // Bodies chosen to break a cut taken any other way than from the whole rendered body: JSON whose compact text
    // differs from its source (exponents, duplicate keys, whitespace), JSON that does not parse, text and JSON with
    // a surrogate pair across the cut, binary, XML, and lengths either side of the cap.
    private static List<Supplier<BodyWithContentType<?>>> corpus() {
        String emojiAtCut = repeat('x', CAP - 1) + "😀" + repeat('y', 100);
        StringBuilder exponents = new StringBuilder("[");
        for (int i = 0; exponents.length() < 2 * CAP; i++) {
            exponents.append(i > 0 ? "," : "").append("1e9,2.50,-0.0,12345678901234567890,");
            exponents.append(i % 7).append("E-3");
        }
        exponents.append(']');
        StringBuilder duplicates = new StringBuilder("{\"k\":\"first\",\"filler\":[");
        for (int i = 0; duplicates.length() < 2 * CAP; i++) {
            duplicates.append(i > 0 ? "," : "").append(i);
        }
        duplicates.append("],\"k\":\"last\"}");
        String pretty = jsonArray("pretty", 3 * CAP / 2).replace(",", ",\n    ").replace("{", "{\n  ");
        byte[] binary = new byte[3 * CAP];
        for (int i = 0; i < binary.length; i++) {
            binary[i] = (byte) (i * 31);
        }
        String jsonAtCut = "{\"a\":\"" + repeat('x', CAP - 7) + "😀" + repeat('z', 50) + "\"}";
        String text = jsonArray("text", 2 * CAP);
        return Arrays.asList(
            () -> json(jsonArray("plain", 4 * CAP)),
            () -> json(exponents.toString()),
            () -> json(duplicates.toString()),
            () -> json(pretty),
            () -> json(jsonAtCut),
            () -> json("{\"unterminated\": \"" + repeat("abcé", CAP)),
            () -> json(jsonArray("small", 100)),
            () -> text(emojiAtCut),
            () -> text(text),
            () -> text(repeat('c', CAP)),
            () -> text(repeat('d', CAP + 1)),
            () -> text(""),
            () -> new BinaryBody(binary.clone()),
            () -> new XmlBody("<a>" + repeat("<b>café</b>", CAP / 4) + "</a>")
        );
    }

    private static BodyWithContentType<?> json(String json) {
        JsonBody body = new JsonBody(json, json.getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_JSON_UTF_8, MatchType.ONLY_MATCHING_FIELDS);
        body.releaseDerivedForms();
        return body;
    }

    private static BodyWithContentType<?> text(String text) {
        StringBody body = new StringBody(text, text.getBytes(StandardCharsets.UTF_8), false, MediaType.PLAIN_TEXT_UTF_8);
        body.releaseDerivedForms();
        return body;
    }

    @Test
    public void shouldShowFromAKeptCutExactlyWhatAFreshDecodeShows() throws Exception {
        List<Supplier<BodyWithContentType<?>>> corpus = corpus();
        for (int i = 0; i < corpus.size(); i++) {
            Supplier<BodyWithContentType<?>> body = corpus.get(i);
            List<LogEntry> retained = exchange("/corpus-" + i, body.get(), body.get());
            // the same bodies, never shown before, so nothing is kept for them
            String fresh = update(exchange("/corpus-" + i, body.get(), body.get()), configuration());

            String firstUpdate = update(retained, configuration());
            String laterUpdate = update(retained, configuration());

            assertThat("corpus body " + i + ", first update", firstUpdate, is(fresh));
            assertThat("corpus body " + i + ", later update", laterUpdate, is(fresh));
        }
    }

    @Test
    public void shouldNotShowAKeptCutWhenRedactionIsOn() throws Exception {
        String json = "{\"password\":\"hunter2-secret\",\"items\":" + jsonArray("item", 3 * CAP) + "}";
        Configuration redacting = configuration().redactSecretsInLog(true).fixtureBodyRedactFields("password");
        List<LogEntry> reverse = exchange("/redacted", json(json), json(json));
        // the same bodies, never shown before, so nothing is kept for them
        String freshRedacted = update(exchange("/redacted", json(json), json(json)), redacting);

        String unredacted = update(reverse, configuration());
        assertThat(unredacted, containsString("hunter2-secret"));

        String redacted = update(reverse, redacting);
        assertThat(redacted, not(containsString("hunter2-secret")));
        assertThat(redacted, containsString("\\\"password\\\":\\\"***REDACTED***\\\""));
        assertThat("a body cut before is redacted as a body never shown", redacted, is(freshRedacted));

        assertThat("redaction off again shows what it showed before", update(reverse, configuration()), is(unredacted));
    }

    @Test
    public void shouldShowEachBodyItsOwnCut() throws Exception {
        List<LogEntry> first = exchange("/first", json(jsonArray("first", 2 * CAP)), json(jsonArray("first", 2 * CAP)));
        List<LogEntry> second = exchange("/second", json(jsonArray("second", 2 * CAP)), json(jsonArray("second", 2 * CAP)));
        update(first, configuration());

        String secondUpdate = update(second, configuration());

        assertThat(secondUpdate, containsString("second-café-0"));
        assertThat(secondUpdate, not(containsString("first-café-0")));
    }
}
