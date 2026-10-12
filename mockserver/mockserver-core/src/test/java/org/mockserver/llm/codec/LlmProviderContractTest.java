package org.mockserver.llm.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockserver.llm.StreamingFormat;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.action.http.HttpLlmResponseActionHandler;
import org.mockserver.model.HttpLlmResponse;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.SseEvent;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.HttpLlmResponseDTO;

import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpRequest.request;

/**
 * Contract tests against real provider responses: each {@code *.contract.json} under
 * {@code src/test/resources/llm/provider-contracts/<provider>/} names a response published by the
 * provider (an API reference example or an SDK's recorded test fixture, cited in its
 * {@code source}), the request that produced it, and the {@code httpLlmResponse} action that
 * asks MockServer for the same content. The test sends the request through
 * {@link HttpLlmResponseActionHandler} and asserts, with {@link ProviderContractShape}, that
 * MockServer's response has the real one's structure: field names, nesting, JSON types,
 * discriminator values, and for streams the wire format and event order.
 * <p>
 * Every difference must be listed in the contract's {@code allowed} array with a reason. An entry
 * with an {@code openItem} names the plan item whose fix will remove it; one without is a permanent,
 * reasoned difference. Either kind fails when it no longer occurs, so the fix for an open item must
 * delete its entries and the list cannot go stale. Unlike the golden files this
 * test never derives its expectation from MockServer's own output; see docs/code/llm-codec-fixtures.md.
 */
@RunWith(Parameterized.class)
public class LlmProviderContractTest {

    private static final ObjectMapper MAPPER = ObjectMapperFactory.createObjectMapper();
    private static final String ROOT = "/llm/provider-contracts";

    private final String name;
    private final Path contractFile;

    public LlmProviderContractTest(String name, Path contractFile) {
        this.name = name;
        this.contractFile = contractFile;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> contracts() throws Exception {
        Path root = contractsRoot();
        try (Stream<Path> files = Files.walk(root)) {
            List<Object[]> cases = files
                .filter(path -> path.getFileName().toString().endsWith(".contract.json"))
                .sorted()
                .map(path -> new Object[]{root.relativize(path).toString().replace(".contract.json", ""), path})
                .collect(Collectors.toList());
            if (cases.isEmpty()) {
                throw new IllegalStateException("no provider contracts found under " + root);
            }
            return cases;
        }
    }

    private static Path contractsRoot() throws URISyntaxException {
        return Paths.get(Objects.requireNonNull(LlmProviderContractTest.class.getResource(ROOT), ROOT).toURI());
    }

    @Test
    public void shouldMatchTheRealProviderResponseStructure() throws Exception {
        JsonNode contract = MAPPER.readTree(contractFile.toFile());
        for (String required : new String[]{"source", "retrieved", "request", "httpLlmResponse", "response", "format"}) {
            assertThat(name + ": contract must declare '" + required + "'", contract.hasNonNull(required), is(true));
        }
        assertThat(name + ": source must be a URL", contract.get("source").asText(), startsWith("https://"));

        HttpLlmResponse action = MAPPER.treeToValue(contract.get("httpLlmResponse"), HttpLlmResponseDTO.class).buildObject();
        HttpRequest request = request(contract.get("request"));
        HttpLlmResponseActionHandler handler = new HttpLlmResponseActionHandler(new MockServerLogger());
        boolean partial = contract.path("partial").asBoolean(false);
        String realBody = new String(Files.readAllBytes(contractFile.resolveSibling(contract.get("response").asText())), StandardCharsets.UTF_8);
        String format = contract.get("format").asText();

        List<ProviderContractShape.Difference> differences = new ArrayList<>();
        boolean streaming = action.getCompletion() != null && Boolean.TRUE.equals(action.getCompletion().getStreaming());
        if (streaming) {
            assertThat(name + ": a streamed contract must declare 'wireFormat'", contract.hasNonNull("wireFormat"), is(true));
            StreamingFormat expectedFormat = StreamingFormat.valueOf(contract.get("wireFormat").asText());
            StreamingFormat actualFormat = handler.streamingFormatFor(action.getProvider(), request);
            if (actualFormat != expectedFormat) {
                differences.add(new ProviderContractShape.Difference(ProviderContractShape.Kind.FORMAT, "stream", "wireFormat",
                    "real " + expectedFormat + ", mock " + actualFormat));
            }
            List<ProviderContractShape.Event> real = withoutIgnoredEvents(realEvents(format, realBody), contract.path("ignoreRealEvents"));
            List<ProviderContractShape.Event> mock = new ArrayList<>();
            for (SseEvent event : handler.handleStreaming(action, request)) {
                mock.add(new ProviderContractShape.Event(event.getEvent(), event.getData()));
            }
            assertThat(name + ": real stream parsed to no events", real, is(not(empty())));
            differences.addAll(ProviderContractShape.compareStreams(real, mock, partial));
        } else {
            assertThat(name + ": a whole response must be JSON", format, is("JSON"));
            HttpResponse response = handler.handle(action, request);
            assertThat(name + ": status", response.getStatusCode(), is(200));
            differences.addAll(ProviderContractShape.compare("body",
                Collections.singletonList(MAPPER.readTree(realBody)),
                Collections.singletonList(MAPPER.readTree(response.getBodyAsString())), partial));
        }
        assertAllowed(contract.path("allowed"), differences);
    }

    private static HttpRequest request(JsonNode spec) {
        HttpRequest request = HttpRequest.request()
            .withMethod(spec.path("method").asText("POST"))
            .withPath(spec.get("path").asText());
        spec.path("queryStringParameters").fields().forEachRemaining(field ->
            request.withQueryStringParameter(field.getKey(), field.getValue().asText()));
        if (spec.has("body")) {
            request.withBody(spec.get("body").toString());
        }
        return request;
    }

    private List<ProviderContractShape.Event> realEvents(String format, String body) throws Exception {
        switch (format) {
            case "SSE":
                return ProviderContractShape.parseSse(body);
            case "EVENTS": {
                List<ProviderContractShape.Event> events = new ArrayList<>();
                for (JsonNode event : MAPPER.readTree(body)) {
                    events.add(new ProviderContractShape.Event(event.path("event").asText(null), event.get("data")));
                }
                return events;
            }
            default:
                throw new IllegalArgumentException(name + ": unknown stream format " + format);
        }
    }

    private static List<ProviderContractShape.Event> withoutIgnoredEvents(List<ProviderContractShape.Event> events, JsonNode ignored) {
        List<ProviderContractShape.Event> kept = new ArrayList<>();
        for (ProviderContractShape.Event event : events) {
            if (event.name == null || !ignored.has(event.name)) {
                kept.add(event);
            }
        }
        return kept;
    }

    private void assertAllowed(JsonNode allowed, List<ProviderContractShape.Difference> differences) {
        List<String> unexplained = new ArrayList<>();
        Set<Integer> used = new HashSet<>();
        for (ProviderContractShape.Difference difference : differences) {
            int match = -1;
            for (int i = 0; i < allowed.size(); i++) {
                if (matches(allowed.get(i), difference)) {
                    match = i;
                    break;
                }
            }
            if (match < 0) {
                unexplained.add(difference.toString());
            } else {
                used.add(match);
            }
        }
        List<String> stale = new ArrayList<>();
        for (int i = 0; i < allowed.size(); i++) {
            JsonNode entry = allowed.get(i);
            assertThat(name + ": allowed[" + i + "] needs a reason", entry.path("reason").asText(""), is(not(emptyString())));
            if (!used.contains(i)) {
                stale.add(entry.toString());
            }
        }
        assertThat(name + ": MockServer's response differs from the real provider's; fix the codec, or if the difference is"
            + " intended add it to 'allowed' with a reason\n  " + String.join("\n  ", unexplained), unexplained, is(empty()));
        assertThat(name + ": these allowed differences no longer occur; delete them\n  " + String.join("\n  ", stale), stale, is(empty()));
    }

    private static boolean matches(JsonNode entry, ProviderContractShape.Difference difference) {
        return difference.kind.name().equals(entry.path("kind").asText())
            && difference.path.equals(entry.path("path").asText())
            && (!entry.hasNonNull("scope") || difference.scope.equals(entry.get("scope").asText()));
    }
}
