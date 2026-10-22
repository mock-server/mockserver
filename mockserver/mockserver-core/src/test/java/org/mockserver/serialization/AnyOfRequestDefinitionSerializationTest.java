package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.ConditionalRequestDefinition;
import org.mockserver.model.RequestDefinition;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.model.AnyOfRequestDefinitionDTO;
import org.mockserver.serialization.model.RequestDefinitionDTO;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.AnyOfRequestDefinition.requestAnyOf;
import static org.mockserver.model.ConditionalRequestDefinition.requestIf;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.OpenAPIDefinition.openAPI;

public class AnyOfRequestDefinitionSerializationTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger();
    private final RequestDefinitionSerializer serializer = new RequestDefinitionSerializer(mockServerLogger);
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger);

    @Test
    public void shouldWriteAnAnyOfArray() {
        String json = serializer.serialize(requestAnyOf(request().withPath("/a"), request().withMethod("POST").withPath("/b")));

        assertThat(json.replaceAll("\\s", ""), is("{\"anyOf\":[{\"path\":\"/a\"},{\"method\":\"POST\",\"path\":\"/b\"}]}"));
    }

    @Test
    public void shouldWriteNotOnlyWhenNegated() {
        assertThat(serializer.serialize(requestAnyOf(request().withPath("/a"))).contains("\"not\""), is(false));
        assertThat(serializer.serialize(requestAnyOf(request().withPath("/a")).withNot(true)).replaceAll("\\s", ""),
            is("{\"not\":true,\"anyOf\":[{\"path\":\"/a\"}]}"));
    }

    @Test
    public void shouldRoundTripEveryBranchKind() {
        AnyOfRequestDefinition original = (AnyOfRequestDefinition) requestAnyOf(
            request().withMethod("GET").withPath("/a").withHeader("X-Env", "prod"),
            openAPI("org/mockserver/openapi/openapi_petstore_example.json", "showPetById"),
            requestIf(request().withMethod("POST"), request().withPath("/orders")),
            requestAnyOf(request().withPath("/x"), request().withPath("/y"))
        ).withNot(true);

        RequestDefinition deserialized = serializer.deserialize(serializer.serialize(original));

        assertThat(deserialized, is(original));
        AnyOfRequestDefinition anyOf = (AnyOfRequestDefinition) deserialized;
        assertThat(anyOf.getAnyOf().get(2), instanceOf(ConditionalRequestDefinition.class));
        assertThat(anyOf.getAnyOf().get(3), instanceOf(AnyOfRequestDefinition.class));
    }

    @Test
    public void shouldRoundTripAnAnyOfInsideAConditional() {
        ConditionalRequestDefinition original = requestIf(request().withMethod("GET"), requestAnyOf(request().withPath("/a"), request().withPath("/b")));

        RequestDefinition deserialized = serializer.deserialize(serializer.serialize(original));

        assertThat(deserialized, is(original));
    }

    @Test
    public void shouldRoundTripAnExpectation() {
        Expectation original = new Expectation(requestAnyOf(request().withPath("/a"), request().withPath("/b")))
            .withId("any-of")
            .thenRespond(response().withBody("either"));

        Expectation deserialized = expectationSerializer.deserialize(expectationSerializer.serialize(original));

        assertThat(deserialized.getHttpRequest(), is((RequestDefinition) original.getHttpRequest()));
        assertThat(deserialized.getHttpResponse().getBodyAsString(), is("either"));
    }

    @Test
    public void shouldReadHandWrittenJson() {
        Expectation expectation = expectationSerializer.deserialize("{" +
            "  \"httpRequest\": { \"anyOf\": [ { \"method\": \"GET\", \"path\": \"/a\" }, { \"path\": \"/b\" } ] }," +
            "  \"httpResponse\": { \"statusCode\": 204 }" +
            "}");

        assertThat(expectation.getHttpRequest(), is((RequestDefinition) requestAnyOf(request().withMethod("GET").withPath("/a"), request().withPath("/b"))));
    }

    @Test
    public void shouldRejectAnEmptyAnyOf() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> expectationSerializer.deserialize("{" +
            "  \"httpRequest\": { \"anyOf\": [ ] }," +
            "  \"httpResponse\": { \"statusCode\": 204 }" +
            "}"));

        assertThat(exception.getMessage(), containsString("anyOf"));
    }

    @Test
    public void shouldRejectRequestFieldsBesideAnyOf() {
        assertThrows(IllegalArgumentException.class, () -> expectationSerializer.deserialize("{" +
            "  \"httpRequest\": { \"path\": \"/a\", \"anyOf\": [ { \"path\": \"/b\" } ] }," +
            "  \"httpResponse\": { \"statusCode\": 204 }" +
            "}"));
    }

    @Test
    public void shouldRejectABinaryBranchInJava() {
        assertThrows(IllegalArgumentException.class, () -> new AnyOfRequestDefinitionDTO(requestAnyOf(BinaryRequestDefinition.binaryRequest("hi".getBytes()))));
        assertThrows(IllegalArgumentException.class, () -> RequestDefinitionDTO.of(requestAnyOf(BinaryRequestDefinition.binaryRequest("hi".getBytes()))));
    }

    @Test
    public void shouldGenerateJavaCode() {
        String java = new ExpectationToJavaSerializer().serialize(0, new Expectation(requestAnyOf(request().withPath("/a"), request().withPath("/b"))).thenRespond(response()));

        assertThat(java, containsString("org.mockserver.model.AnyOfRequestDefinition.requestAnyOf("));
        assertThat(java, containsString(".withPath(\"/a\"),"));
        assertThat(java, containsString(".withPath(\"/b\")"));
    }
}
