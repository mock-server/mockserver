package org.mockserver.serialization;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;
import org.mockserver.serialization.model.HttpRequestDTO;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableString.string;

public class ClientIpSerializationTest {

    private final MockServerLogger mockServerLogger = new MockServerLogger(ClientIpSerializationTest.class);
    private final ExpectationSerializer expectationSerializer = new ExpectationSerializer(mockServerLogger);
    private final HttpRequestSerializer httpRequestSerializer = new HttpRequestSerializer(mockServerLogger);

    @Test
    public void shouldRoundTripClientIpThroughExpectationJson() {
        for (String clientIp : new String[]{"10.0.0.0/8", "!10.0.0.0/8", "192.168.1.10", "2001:db8::/32", "!::1"}) {
            Expectation original = new Expectation(request().withPath("/api").withClientIp(clientIp)).thenRespond(response().withBody("ok"));
            String json = expectationSerializer.serialize(original);
            assertThat(json, containsString("\"clientIp\" : \"" + clientIp + "\""));
            Expectation roundTripped = expectationSerializer.deserialize(json);
            assertThat(roundTripped, is(original));
            assertThat(((HttpRequest) roundTripped.getHttpRequest()).getClientIp(), is(string(clientIp)));
        }
    }

    @Test
    public void shouldAcceptTheObjectFormOfClientIp() {
        Expectation expectation = expectationSerializer.deserialize("{" + NEW_LINE +
            "  \"httpRequest\" : { \"path\" : \"/api\", \"clientIp\" : { \"not\" : true, \"value\" : \"10.0.0.0/8\" } }," + NEW_LINE +
            "  \"httpResponse\" : { \"statusCode\" : 200 }" + NEW_LINE +
            "}");
        HttpRequest httpRequest = (HttpRequest) expectation.getHttpRequest();
        assertThat(httpRequest.getClientIp().isNot(), is(true));
        assertThat(httpRequest.getClientIp().getValue(), is("10.0.0.0/8"));
    }

    @Test
    public void shouldOmitClientIpWhenUnset() {
        String json = expectationSerializer.serialize(new Expectation(request().withPath("/api")).thenRespond(response()));
        assertThat(json, not(containsString("clientIp")));
        assertThat(httpRequestSerializer.serialize(request().withPath("/api").withRemoteAddress("10.0.0.1:5000")), not(containsString("clientIp")));
    }

    @Test
    public void shouldRoundTripClientIpThroughRequestJsonAndDto() {
        HttpRequest original = request().withPath("/api").withClientIp("!172.16.0.0/12");
        assertThat(httpRequestSerializer.deserialize(httpRequestSerializer.serialize(original)), is(original));
        assertThat(new HttpRequestDTO(original).buildObject(), is(original));
        assertThat(new HttpRequestDTO(request()).buildObject().getClientIp(), is(nullValue()));
    }

    @Test
    public void shouldRejectAClientIpThatIsNotAnAddressOrRange() {
        IllegalArgumentException invalidAddress = assertThrows(IllegalArgumentException.class, () -> expectationSerializer.deserialize("{" + NEW_LINE +
            "  \"httpRequest\" : { \"clientIp\" : \"example.com\" }," + NEW_LINE +
            "  \"httpResponse\" : { \"statusCode\" : 200 }" + NEW_LINE +
            "}"));
        assertThat(invalidAddress.getMessage(), containsString("because clientIp \"example.com\" is not an IP address or CIDR range"));

        IllegalArgumentException wrongType = assertThrows(IllegalArgumentException.class, () -> expectationSerializer.deserialize("{" + NEW_LINE +
            "  \"httpRequest\" : { \"clientIp\" : 10 }," + NEW_LINE +
            "  \"httpResponse\" : { \"statusCode\" : 200 }" + NEW_LINE +
            "}"));
        assertThat(wrongType.getMessage(), containsString("clientIp"));

        IllegalArgumentException schemaObject = assertThrows(IllegalArgumentException.class, () -> expectationSerializer.deserialize("{" + NEW_LINE +
            "  \"httpRequest\" : { \"clientIp\" : { \"schema\" : { \"type\" : \"string\" } } }," + NEW_LINE +
            "  \"httpResponse\" : { \"statusCode\" : 200 }" + NEW_LINE +
            "}"));
        assertThat(schemaObject.getMessage(), containsString("clientIp"));
    }

    @Test
    public void shouldGenerateJavaThatSetsTheClientIp() {
        String plain = new ExpectationToJavaSerializer().serialize(0, new Expectation(request().withPath("/api").withClientIp("10.0.0.0/8")).thenRespond(response()));
        assertThat(plain, containsString(".withClientIp(\"10.0.0.0/8\")"));
        String negated = new ExpectationToJavaSerializer().serialize(0, new Expectation(request().withPath("/api").withClientIp("!2001:db8::/32")).thenRespond(response()));
        assertThat(negated, containsString(".withClientIp(\"!2001:db8::/32\")"));
    }
}
