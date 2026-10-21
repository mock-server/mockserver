package org.mockserver.mock;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.state.ExpectationEntry;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExpectationMetadataTest {

    private final ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());

    private static Expectation expectationWithMetadata() {
        Map<String, Object> nested = new LinkedHashMap<>();
        nested.put("team", "checkout");
        nested.put("oncall", Arrays.asList("ann", "bob"));
        return new Expectation(request().withPath("/some")).withId("with-metadata")
            .withMetadata("owner", "team-a")
            .withMetadata("ticket", 101)
            .withMetadata("flaky", true)
            .withMetadata("ratio", 0.5)
            .withMetadata("tags", Arrays.asList("smoke", "checkout"))
            .withMetadata("contact", nested)
            .withMetadata("note", null)
            .thenRespond(response("body"));
    }

    @Test
    public void metadataRoundTripsThroughJson() {
        Expectation original = expectationWithMetadata();

        String json = serializer.serialize(original);
        Expectation deserialized = serializer.deserialize(json);

        assertThat(json, containsString("\"metadata\" : {"));
        assertThat(deserialized.getMetadata(), is(original.getMetadata()));
        assertThat(deserialized, is(original));
    }

    @Test
    public void theSharedClientFixtureIsAValidExpectationAndRoundTrips() throws Exception {
        // test-fixtures/expectations/top_metadata.json is the corpus every client's fidelity test reads
        String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("../../test-fixtures/expectations/top_metadata.json")), java.nio.charset.StandardCharsets.UTF_8);

        Expectation expectation = serializer.deserialize(json);

        assertThat(expectation.getMetadata().get("literal"), is("!not?optional 100%+a b"));
        assertThat(expectation.getMetadata().containsKey("note"), is(true));
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();
        assertThat(objectMapper.readTree(serializer.serialize(expectation)).get("metadata"), is(objectMapper.readTree(json).get("metadata")));
    }

    @Test
    public void jsonWithoutMetadataIsUnchanged() {
        String json = serializer.serialize(new Expectation(request().withPath("/some")).withId("plain").thenRespond(response("body")));

        assertThat(json, not(containsString("metadata")));
    }

    @Test
    public void jsonSchemaAcceptsMetadataAndRejectsANonObject() {
        assertThat(serializer.deserialize("{\"httpRequest\":{\"path\":\"/a\"},\"httpResponse\":{},\"metadata\":{\"owner\":\"team-a\",\"tags\":[1,2]}}").getMetadata().get("owner"), is("team-a"));
        assertThrows(IllegalArgumentException.class, () -> serializer.deserialize("{\"httpRequest\":{\"path\":\"/a\"},\"httpResponse\":{},\"metadata\":[\"not\",\"an\",\"object\"]}"));
    }

    @Test
    public void metadataSurvivesTheClusteredStateCarrier() throws Exception {
        Expectation original = expectationWithMetadata();

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(new ExpectationEntry(original));
        }
        ExpectationEntry copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            copy = (ExpectationEntry) in.readObject();
        }

        assertThat(copy.getExpectation().getMetadata(), is(original.getMetadata()));
    }

    @Test
    public void metadataIsCopiedUnmodifiableAndSharedByClones() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("owner", "team-a");
        Expectation expectation = new Expectation(request()).withMetadata(source);
        source.put("owner", "changed");

        assertThat(expectation.getMetadata().get("owner"), is("team-a"));
        assertThrows(UnsupportedOperationException.class, () -> expectation.getMetadata().put("x", "y"));
        assertThat(expectation.clone().getMetadata() == expectation.getMetadata(), is(true));
        assertThat(expectation.cloneWith(request("/other"), null).getMetadata(), is(expectation.getMetadata()));
        assertThat(new Expectation(request()).withMetadata("a", 1).withMetadata((Map<String, Object>) null).getMetadata(), nullValue());
        assertThat(new Expectation(request()).withMetadata(Collections.emptyMap()).getMetadata(), nullValue());
        assertThrows(IllegalArgumentException.class, () -> new Expectation(request()).withMetadata(" ", "value"));
    }

    @Test
    public void withMetadataJsonParsesTheValue() {
        Expectation expectation = new Expectation(request()).withMetadataJson("tags", "[\"a\",{\"b\":2}]");

        assertThat(expectation.getMetadata().get("tags"), is(Arrays.asList("a", Collections.singletonMap("b", 2))));
        assertThrows(IllegalArgumentException.class, () -> new Expectation(request()).withMetadataJson("tags", "[not json"));
    }

    @Test
    public void metadataTakesPartInEquality() {
        Expectation plain = new Expectation(request("/a")).withId("x").thenRespond(response());
        Expectation tagged = new Expectation(request("/a")).withId("x").withMetadata("owner", "team-a").thenRespond(response());

        assertThat(plain, is(new Expectation(request("/a")).withId("x").thenRespond(response())));
        assertThat(tagged, not(plain));
        assertThat(tagged, is(new Expectation(request("/a")).withId("x").withMetadata("owner", "team-a").thenRespond(response())));
        assertThat(tagged.hashCode(), is(new Expectation(request("/a")).withId("x").withMetadata("owner", "team-a").thenRespond(response()).hashCode()));
    }

    @Test
    public void anUpsertThatOnlyChangesMetadataIsApplied() {
        RequestMatchers requestMatchers = new RequestMatchers(configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        requestMatchers.add(new Expectation(request("/a")).withId("x").withMetadata("owner", "team-a").thenRespond(response()), API);

        requestMatchers.add(new Expectation(request("/a")).withId("x").withMetadata("owner", "team-b").thenRespond(response()), API);

        assertThat(requestMatchers.retrieveActiveExpectations(null).get(0).getMetadata().get("owner"), is("team-b"));
    }

    @Test
    public void metadataNeverAffectsMatching() {
        RequestMatchers requestMatchers = new RequestMatchers(configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        requestMatchers.add(expectationWithMetadata(), API);

        assertThat(requestMatchers.firstMatchingExpectation(request("/some")).getId(), is("with-metadata"));
        assertThat(requestMatchers.firstMatchingExpectation(request("/other")), nullValue());
    }

    @Test
    public void metadataIsSerialisedOncePerChangeAndWrittenRaw() throws Exception {
        Expectation expectation = expectationWithMetadata();
        String first = expectation.getMetadataJson();

        assertThat(expectation.getMetadataJson() == first, is(true));
        assertThat(new org.mockserver.serialization.model.ExpectationDTO(expectation).getMetadataJson() == first, is(true));
        assertThat(expectation.clone().getMetadataJson() == first, is(true));
        expectation.withMetadata("owner", "team-b");
        assertThat(expectation.getMetadataJson(), containsString("\"owner\":\"team-b\""));

        // the dashboard builds a tree from the DTO and writes it again
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = org.mockserver.serialization.ObjectMapperFactory.createObjectMapper();
        String viaTree = objectMapper.writeValueAsString(objectMapper.valueToTree(new org.mockserver.serialization.model.ExpectationDTO(expectation)));
        assertThat(objectMapper.readTree(viaTree).get("metadata").get("owner").asText(), is("team-b"));
    }

    @Test
    public void metadataIsLeftOutOfTheMatchLogClone() {
        Expectation expectation = expectationWithMetadata();

        Expectation forLog = expectation.cloneWithoutMetadata();

        assertThat(forLog.getMetadata(), nullValue());
        assertThat(forLog.getMetadataJson(), nullValue());
        assertThat(forLog.getId(), is(expectation.getId()));
        assertThat(expectation.getMetadata().get("owner"), is("team-a"));
        assertThat(new Expectation(request()).cloneWithoutMetadata().getMetadata(), nullValue());
    }

    @Test
    public void theMatchLogEntriesDoNotCarryMetadata() {
        MockServerLogger logger = mock(MockServerLogger.class);
        org.mockito.Mockito.when(logger.isEnabledForInstance(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        RequestMatchers requestMatchers = new RequestMatchers(configuration(), logger, mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        requestMatchers.add(new Expectation(request("/labelled")).withId("labelled").withMetadata("label", "only-on-retrieve").thenRespond(response()), API);
        requestMatchers.add(new Expectation(request("/other")).withId("other").withMetadata("label", "only-on-retrieve").thenRespond(response()), API);
        org.mockito.Mockito.clearInvocations(logger);

        assertThat(requestMatchers.firstMatchingExpectation(request("/labelled")).getId(), is("labelled"));
        assertThat(requestMatchers.firstMatchingExpectation(request("/unknown")), nullValue());

        org.mockito.ArgumentCaptor<org.mockserver.log.model.LogEntry> entries = org.mockito.ArgumentCaptor.forClass(org.mockserver.log.model.LogEntry.class);
        org.mockito.Mockito.verify(logger, org.mockito.Mockito.atLeastOnce()).logEvent(entries.capture());
        int expectationArguments = 0;
        for (org.mockserver.log.model.LogEntry entry : entries.getAllValues()) {
            for (Object argument : entry.getArguments() != null ? entry.getArguments() : new Object[0]) {
                if (argument instanceof Expectation) {
                    expectationArguments++;
                    assertThat(entry.getType() + " " + entry.getMessageFormat(), ((Expectation) argument).getMetadata(), nullValue());
                }
            }
        }
        // the matched entry, and the not-matched / closest-match entries for the unknown request
        assertThat(expectationArguments >= 2, is(true));
        assertThat(requestMatchers.retrieveActiveExpectations(null).get(0).getMetadata().get("label"), is("only-on-retrieve"));
    }

    @Test
    public void javaTimeValuesAreWrittenTheSameWayByTheExpectationAndTheFilter() {
        java.time.LocalDate since = java.time.LocalDate.of(2026, 10, 21);
        Expectation expectation = new Expectation(request("/dated")).withId("dated").withMetadata("since", since).thenRespond(response());

        Expectation deserialized = serializer.deserialize(serializer.serialize(expectation));

        assertThat(ExpectationFilter.expectationFilter().withMetadata("since", since).matches(deserialized), is(true));
        assertThat(ExpectationFilter.expectationFilter().withMetadata("since", since).matches(expectation), is(true));
    }

    @Test
    public void heapEstimateCountsMetadata() {
        long without = new Expectation(request("/some")).withId("x").thenRespond(response("body")).estimatedHeapSize();
        Expectation with = new Expectation(request("/some")).withId("x").withMetadata("note", String.join("", Collections.nCopies(1000, "x"))).thenRespond(response("body"));

        assertThat(with.estimatedHeapSize() - without >= 1000 * 20, is(true));
        assertThat(new Expectation(request("/some")).withMetadata("pojo", new Object()).estimatedHeapSize() > 0, is(true));
    }

    @Test
    public void javaCodeIncludesMetadata() {
        String java = new ExpectationToJavaSerializer().serialize(0, expectationWithMetadata());

        assertThat(java, containsString(".withMetadata(\"owner\", \"team-a\")"));
        assertThat(java, containsString(".withMetadata(\"ticket\", 101)"));
        assertThat(java, containsString(".withMetadata(\"flaky\", true)"));
        assertThat(java, containsString(".withMetadata(\"ratio\", 0.5)"));
        assertThat(java, containsString(".withMetadata(\"note\", (Object) null)"));
        assertThat(java, containsString(".withMetadataJson(\"tags\", \"[\\\"smoke\\\",\\\"checkout\\\"]\")"));
        assertThat(java, containsString(".withMetadataJson(\"contact\", \"{\\\"team\\\":\\\"checkout\\\",\\\"oncall\\\":[\\\"ann\\\",\\\"bob\\\"]}\")"));
        assertThat(new ExpectationToJavaSerializer().serialize(0, new Expectation(request()).withMetadata("node", JsonNodeFactory.instance.objectNode().put("k", 1))),
            containsString(".withMetadataJson(\"node\", \"{\\\"k\\\":1}\")"));
    }
}
