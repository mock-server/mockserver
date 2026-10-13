package org.mockserver.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jsonpatch.JsonPatch;
import com.github.fge.jsonpatch.JsonPatchException;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.fixture.SensitiveValueMatcher;
import org.mockserver.log.MockServerEventLog;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.MatchDifference;
import org.mockserver.matchers.MatchType;
import org.mockserver.matchers.MatcherBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonBody.json;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

public class ExpectationFixSuggesterTest {

    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(configuration, ExpectationFixSuggesterTest.class);
    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger, configuration);
    private final MatcherBuilder matcherBuilder = new MatcherBuilder(configuration, mockServerLogger);
    private final ExpectationFixSuggester suggester = new ExpectationFixSuggester(objectMapper, expectationSerializer,
        new RequestDefinitionSerializer(mockServerLogger), matcherBuilder);

    @Test
    public void shouldReplaceOnlyTheDifferingPathGuardedByATestOfItsCurrentValue() throws Exception {
        Expectation expectation = expectation(request().withMethod("GET").withPath("/order").withHeader("Accept", "application/json"));
        HttpRequest recorded = request().withMethod("GET").withPath("/orders").withHeader("Accept", "application/json");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").toString(), is("[" +
            "{\"op\":\"test\",\"path\":\"/httpRequest/path\",\"value\":\"/order\"}," +
            "{\"op\":\"replace\",\"path\":\"/httpRequest/path\",\"value\":\"/orders\"}" +
            "]"));
        assertThat(fix.path("patchedFields").toString(), is("[\"path\"]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldNotApplyWhenTheExpectationChangedSinceThePatchWasMade() throws Exception {
        Expectation expectation = expectation(request().withMethod("GET").withPath("/order"));
        ObjectNode fix = suggest(expectation, request().withMethod("GET").withPath("/orders"), SensitiveValueMatcher.of(null));

        JsonNode changedSince = objectMapper.readTree(expectationSerializer.serialize(expectation(request().withMethod("GET").withPath("/changed"))));

        assertThrows(JsonPatchException.class, () -> JsonPatch.fromJson(fix.path("patch")).apply(changedSince));
    }

    @Test
    public void shouldReplaceTheValuesOfAHeaderTheRequestSendsDifferentlyAndKeepTheOthers() throws Exception {
        Expectation expectation = expectation(request().withPath("/api")
            .withHeader("Accept", "application/json")
            .withHeader("X-Tenant", "blue"));
        HttpRequest recorded = request().withMethod("GET").withPath("/api")
            .withHeader("accept", "application/json")
            .withHeader("x-tenant", "green");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").toString(), is("[" +
            "{\"op\":\"test\",\"path\":\"/httpRequest/headers/X-Tenant\",\"value\":[\"blue\"]}," +
            "{\"op\":\"replace\",\"path\":\"/httpRequest/headers/X-Tenant\",\"value\":[\"green\"]}" +
            "]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldRemoveAQueryParameterTheRequestDoesNotSend() throws Exception {
        Expectation expectation = expectation(request().withPath("/search")
            .withQueryStringParameter("q", "shoes")
            .withQueryStringParameter("page", "2"));
        HttpRequest recorded = request().withMethod("GET").withPath("/search").withQueryStringParameter("q", "shoes");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").toString(), is("[" +
            "{\"op\":\"test\",\"path\":\"/httpRequest/queryStringParameters/page\",\"value\":[\"2\"]}," +
            "{\"op\":\"remove\",\"path\":\"/httpRequest/queryStringParameters/page\"}" +
            "]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldPatchOnlyTheContradictedFieldsOfAJsonBodyAndKeepPlaceholders() throws Exception {
        Expectation expectation = expectation(request().withMethod("POST").withPath("/orders")
            .withBody(json("{\"sku\":\"A1\",\"quantity\":2,\"note\":\"${json-unit.any-string}\",\"gift\":true}")));
        HttpRequest recorded = request().withMethod("POST").withPath("/orders")
            .withBody(json("{\"sku\":\"A1\",\"quantity\":3,\"note\":\"leave at door\",\"extra\":1}"));

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").toString(), is("[" +
            "{\"op\":\"test\",\"path\":\"/httpRequest/body/quantity\",\"value\":2}," +
            "{\"op\":\"replace\",\"path\":\"/httpRequest/body/quantity\",\"value\":3}," +
            "{\"op\":\"test\",\"path\":\"/httpRequest/body/gift\",\"value\":true}," +
            "{\"op\":\"remove\",\"path\":\"/httpRequest/body/gift\"}" +
            "]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldReplaceAStrictJsonBodyWithTheRequestsBody() throws Exception {
        Expectation expectation = expectation(request().withPath("/orders")
            .withBody(json("{\"sku\":\"A1\"}", MatchType.STRICT)));
        HttpRequest recorded = request().withMethod("POST").withPath("/orders").withBody(json("{\"sku\":\"A1\",\"quantity\":3}"));

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patchedFields").toString(), is("[\"body\"]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldReplaceAPathParameterValueTheRequestContradicts() throws Exception {
        Expectation expectation = expectation(request().withPath("/users/{id}").withPathParameter("id", "[0-9]+"));
        HttpRequest recorded = request().withMethod("GET").withPath("/users/abc");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").toString(), is("[" +
            "{\"op\":\"test\",\"path\":\"/httpRequest/pathParameters/id\",\"value\":[\"[0-9]+\"]}," +
            "{\"op\":\"replace\",\"path\":\"/httpRequest/pathParameters/id\",\"value\":[\"abc\"]}" +
            "]"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldDropPathParametersWhenThePathTemplateIsReplacedByTheLiteralPath() throws Exception {
        Expectation expectation = expectation(request().withMethod("GET").withPath("/users/{id}").withPathParameter("id", "[0-9]+"));
        HttpRequest recorded = request().withMethod("GET").withPath("/accounts/5");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").toString(), containsString("{\"op\":\"remove\",\"path\":\"/httpRequest/pathParameters\"}"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldSayWhenThePatchedFieldsAloneDoNotMakeItMatch() throws Exception {
        Expectation expectation = expectation(request().withMethod("POST").withPath("/upload").withBody("expected"));
        HttpRequest recorded = request().withMethod("PUT").withPath("/upload").withBody("first part of a long bo")
            .withHeader(MockServerEventLog.TRUNCATED_BODY_HEADER, "true");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patchedFields").toString(), is("[\"method\"]"));
        assertThat(fix.path("unpatchedFields").get(0).path("field").asText(), is("body"));
        assertThat("the body still differs, so the patched expectation does not match", fix.path("matchesAfterPatch").asBoolean(), is(false));
    }

    @Test
    public void shouldFixSeveralFieldsInOnePatch() throws Exception {
        Expectation expectation = expectation(request().withMethod("POST").withPath("/order").withSecure(true));
        HttpRequest recorded = request().withMethod("PUT").withPath("/orders").withSecure(false);

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patchedFields").toString(), is("[\"method\",\"path\",\"secure\"]"));
        assertThat(fix.path("unpatchedFields").size(), is(0));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(true));
        assertPatchedExpectationMatches(expectation, fix, recorded);
    }

    @Test
    public void shouldNotCopyARedactedValueIntoThePatch() throws Exception {
        Expectation expectation = expectation(request().withPath("/api").withHeader("Authorization", "Bearer expected"));
        HttpRequest recorded = request().withMethod("GET").withPath("/api").withHeader("Authorization", "Bearer s3cr3t-token");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(Collections.singletonList("s3cr3t-token")));

        assertThat(fix.path("patch").size(), is(0));
        assertThat(fix.toString(), not(containsString("s3cr3t-token")));
        assertThat(fix.path("unpatchedFields").get(0).path("field").asText(), is("headers"));
        assertThat(fix.path("unpatchedFields").get(0).path("reason").asText(), containsString("redacts"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(false));
    }

    @Test
    public void shouldNotCopyARedactedNumericValueIntoThePatch() throws Exception {
        Expectation expectation = expectation(request().withPath("/pay").withBody(json("{\"card\":1111222233334444,\"amount\":1}")));
        HttpRequest recorded = request().withMethod("POST").withPath("/pay").withBody(json("{\"card\":4929123412341234,\"amount\":1}"));

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(Collections.singletonList("4929123412341234")));

        assertThat(fix.toString(), not(containsString("4929123412341234")));
        assertThat(fix.path("patch").size(), is(0));
        assertThat(fix.path("unpatchedFields").get(0).path("field").asText(), is("body"));
    }

    @Test
    public void shouldNotCopyATruncatedBody() throws Exception {
        Expectation expectation = expectation(request().withPath("/upload").withBody("expected"));
        HttpRequest recorded = request().withMethod("POST").withPath("/upload").withBody("first part of a long bo")
            .withHeader(MockServerEventLog.TRUNCATED_BODY_HEADER, "true");

        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").size(), is(0));
        assertThat(fix.path("unpatchedFields").get(0).path("field").asText(), is("body"));
        assertThat(fix.path("unpatchedFields").get(0).path("reason").asText(), containsString("maxLoggedBodyBytes"));
    }

    @Test
    public void shouldExplainThatAnOpenApiExpectationCannotBePatched() {
        Expectation expectation = new Expectation(openAPI("org/mockserver/openapi/openapi_petstore_example.json", "showPetById")).withId("spec").thenRespond(response());

        ObjectNode fix = suggester.suggest(expectation, request().withPath("/pets/1"), ExpectationFixSuggester.differingFields(objectMapper.createObjectNode()), SensitiveValueMatcher.of(null));

        assertThat(fix.path("patch").size(), is(0));
        assertThat(fix.path("unpatchedFields").get(0).path("field").asText(), is("httpRequest"));
        assertThat(fix.path("matchesAfterPatch").asBoolean(), is(false));
    }

    @Test
    public void shouldNotOverwriteAMemberAddedSinceThePatchWasMade() throws Exception {
        Expectation expectation = expectation(request().withPath("/orders").withBody(json("{\"sku\":\"A1\"}", MatchType.STRICT)));
        HttpRequest recorded = request().withMethod("POST").withPath("/orders").withBody(json("{\"sku\":\"A1\",\"quantity\":3}"));
        ObjectNode fix = suggest(expectation, recorded, SensitiveValueMatcher.of(null));

        JsonNode addedSince = objectMapper.readTree(expectationSerializer.serialize(expectation(request().withPath("/orders")
            .withBody(json("{\"sku\":\"A1\",\"quantity\":5}", MatchType.STRICT)))));

        assertThat(fix.path("patch").toString(), containsString("\"op\":\"add\""));
        assertThrows(JsonPatchException.class, () -> JsonPatch.fromJson(fix.path("patch")).apply(addedSince));
    }

    @Test
    public void shouldDiffObjectsIntoAddRemoveAndGuardedReplaceOperations() throws Exception {
        List<ObjectNode> operations = new ArrayList<>();
        ExpectationFixSuggester.diff("/httpRequest",
            objectMapper.readTree("{\"a/b\":1,\"keep\":true,\"gone\":\"x\",\"list\":[1,2]}"),
            objectMapper.readTree("{\"a/b\":2,\"keep\":true,\"list\":[2],\"new~\":0}"),
            operations);

        assertThat(operations.toString(), is("[" +
            "{\"op\":\"test\",\"path\":\"/httpRequest\",\"value\":{\"a/b\":1,\"keep\":true,\"gone\":\"x\",\"list\":[1,2]}}, " +
            "{\"op\":\"test\",\"path\":\"/httpRequest/a~1b\",\"value\":1}, " +
            "{\"op\":\"replace\",\"path\":\"/httpRequest/a~1b\",\"value\":2}, " +
            "{\"op\":\"test\",\"path\":\"/httpRequest/gone\",\"value\":\"x\"}, " +
            "{\"op\":\"remove\",\"path\":\"/httpRequest/gone\"}, " +
            "{\"op\":\"test\",\"path\":\"/httpRequest/list\",\"value\":[1,2]}, " +
            "{\"op\":\"replace\",\"path\":\"/httpRequest/list\",\"value\":[2]}, " +
            "{\"op\":\"add\",\"path\":\"/httpRequest/new~0\",\"value\":0}" +
            "]"));
    }

    // ---- helpers ----

    private static Expectation expectation(HttpRequest httpRequest) {
        return new Expectation(httpRequest).withId("exp-1").thenRespond(response().withBody("ok"));
    }

    private ObjectNode suggest(Expectation expectation, HttpRequest recorded, SensitiveValueMatcher sensitiveValues) {
        ObjectNode comparison = RequestMatchExplainer.compareFields(objectMapper, matcherBuilder.transformsToMatcher(expectation), expectation, recorded, SensitiveValueMatcher.of(null));
        Set<MatchDifference.Field> differingFields = ExpectationFixSuggester.differingFields(comparison);
        assertThat("the fixture must not already match", comparison.path("matches").asBoolean(), is(false));
        assertThat(differingFields, not(empty()));
        return suggester.suggest(expectation, recorded, differingFields, sensitiveValues);
    }

    /**
     * Applies the patch, with an independent RFC 6902 implementation, to the expectation as the retrieve endpoint
     * returns it, reads the result back as PUT /mockserver/expectation would, and checks it matches the request.
     */
    private void assertPatchedExpectationMatches(Expectation expectation, ObjectNode fix, HttpRequest recorded) throws Exception {
        JsonNode retrieved = objectMapper.readTree(expectationSerializer.serialize(expectation));
        JsonNode patched = JsonPatch.fromJson(fix.path("patch")).apply(retrieved);
        JsonNode patchedOutsideRequest = ((ObjectNode) patched.deepCopy()).without("httpRequest");
        JsonNode retrievedOutsideRequest = ((ObjectNode) retrieved.deepCopy()).without("httpRequest");
        assertThat("the patch changes nothing outside httpRequest", patchedOutsideRequest, is(retrievedOutsideRequest));
        Expectation applied = expectationSerializer.deserialize(objectMapper.writeValueAsString(patched));
        assertThat(applied.getId(), is(expectation.getId()));
        HttpRequest clone = recorded.clone().removeHeader(MockServerEventLog.TRUNCATED_BODY_HEADER);
        assertThat("the patched expectation matches the recorded request",
            matcherBuilder.transformsToMatcher(applied).matches(new MatchDifference(false, clone).suppressMatchResultLogging(), clone), is(true));
    }
}
