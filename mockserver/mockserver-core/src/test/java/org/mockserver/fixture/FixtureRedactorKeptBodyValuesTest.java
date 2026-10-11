package org.mockserver.fixture;

import org.junit.Test;
import org.mockserver.matchers.MatchType;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.RequestDefinition;
import org.mockserver.model.StringBody;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * The field values found in a large body are kept, so a retained log entry rendered again (each dashboard update) does
 * not decode and parse its bodies again. A kept list must be the list a fresh search finds, and must be used only for
 * the field names and Content-Type it was found under: a list found under other names would scrub too little.
 */
public class FixtureRedactorKeptBodyValuesTest {

    private static final int LARGE = FixtureRedactor.KEPT_BODY_VALUES_MIN_CHARACTERS;

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

    private static FixtureRedactor redactor(String... bodyFields) {
        return new FixtureRedactor(FixtureRedactor.defaultSensitiveHeaders(), Arrays.asList(bodyFields));
    }

    private static String json(int minimumLength) {
        StringBuilder json = new StringBuilder("{\"password\":\"pw-secret-0\",\"token\":\"tk-secret-0\",\"items\":[");
        for (int i = 0; json.length() < minimumLength; i++) {
            json.append(i > 0 ? "," : "").append("{\"id\":").append(i)
                .append(",\"password\":\"pw-secret-").append(i % 50)
                .append("\",\"token\":\"tk-secret-").append(i % 50).append("\"}");
        }
        return json.append("]}").toString();
    }

    private static List<String> values(FixtureRedactor redactor, Body<?> body, String contentType) {
        HttpRequest request = request("/").withBody(body);
        if (contentType != null) {
            request.withHeader("Content-Type", contentType);
        }
        return redactor.sensitiveValues(new RequestDefinition[]{request}, null);
    }

    @Test
    public void shouldSearchALargeBodyOnceForTheSameFields() {
        CountingJsonBody body = new CountingJsonBody(json(2 * LARGE));

        List<String> first = values(redactor("password"), body, null);
        List<String> second = values(redactor("password"), body, null);

        // a kept list is a soft reference: allow one search more should a collection clear it in between
        assertThat(body.decodes.get(), lessThanOrEqualTo(2));
        assertThat(second, is(first));
        assertThat(first, hasItem("pw-secret-49"));
    }

    @Test
    public void shouldFindForOtherFieldsWhatAFreshSearchFinds() {
        String json = json(2 * LARGE);
        JsonBody body = new JsonBody(json);
        List<List<String>> sequence = Arrays.asList(
            Collections.singletonList("password"), Collections.singletonList("token"), Arrays.asList("password", "token"),
            Collections.singletonList("Password"), Collections.singletonList("other"), Collections.singletonList("password")
        );
        for (List<String> fields : sequence) {
            String[] names = fields.toArray(new String[0]);
            assertThat("fields " + fields, values(redactor(names), body, null), is(values(redactor(names), new JsonBody(json), null)));
        }
        assertThat(values(redactor("token"), body, null), not(hasItem("pw-secret-0")));
        assertThat(values(redactor("token"), body, null), hasItem("tk-secret-0"));
    }

    @Test
    public void shouldFindUnderEachContentTypeWhatAFreshSearchFinds() {
        // with no Content-Type a binary body is searched as UTF-8 text, with one as base64, where no field is found
        byte[] bytes = json(2 * LARGE).getBytes(StandardCharsets.UTF_8);
        for (List<String> order : Arrays.asList(Arrays.asList("application/octet-stream", null), Arrays.asList(null, "application/octet-stream"))) {
            BinaryBody shared = new BinaryBody(bytes);
            for (String contentType : order) {
                assertThat("Content-Type " + contentType, values(redactor("password"), shared, contentType), is(values(redactor("password"), new BinaryBody(bytes), contentType)));
            }
            assertThat(values(redactor("password"), shared, null), hasItem("pw-secret-0"));
        }
    }

    @Test
    public void shouldFindInEveryBodyTypeWhatAFreshSearchFinds() {
        String json = json(2 * LARGE);
        String sse = "data: " + json.replace("\n", "") + "\n\n";
        List<Supplier<Body<?>>> bodies = Arrays.asList(
            () -> new JsonBody(json),
            () -> new StringBody(json),
            () -> new StringBody(sse),
            () -> new StringBody(json.substring(0, json.length() - 10)),
            () -> new BinaryBody(json.getBytes(StandardCharsets.UTF_8)),
            () -> new StringBody(json(LARGE / 4))
        );
        for (int i = 0; i < bodies.size(); i++) {
            Body<?> retained = bodies.get(i).get();
            List<String> fresh = values(redactor("password"), bodies.get(i).get(), null);
            assertThat("body " + i + ", first search", values(redactor("password"), retained, null), is(fresh));
            assertThat("body " + i + ", later search", values(redactor("password"), retained, null), is(fresh));
        }
    }
}
