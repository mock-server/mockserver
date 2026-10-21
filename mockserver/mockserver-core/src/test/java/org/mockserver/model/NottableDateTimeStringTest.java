package org.mockserver.model;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.NottableDateTimeString.after;
import static org.mockserver.model.NottableDateTimeString.before;
import static org.mockserver.model.NottableDateTimeString.equalToDateTime;
import static org.mockserver.model.NottableString.string;

public class NottableDateTimeStringTest {

    private final ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());

    private Expectation expectation() {
        return new Expectation(
            request()
                .withPath("/orders")
                .withHeader(string("If-Modified-Since"), before("now").withActualFormat("rfc1123"))
                .withQueryStringParameter(string("from"), after("2026-01-01").withActualFormat("dd/MM/yyyy").withZone("Europe/London"))
                .withCookie(string("seen"), equalToDateTime("now").withTruncateExpected("startOfDay").withTruncateActual("startOfDay").withApplyTruncationLast(false).withNot(true))
        ).thenRespond(response().withBody("orders"));
    }

    @Test
    public void shouldBeImmutable() {
        NottableDateTimeString base = after("now");
        NottableDateTimeString zoned = base.withZone("Europe/London");
        assertThat(base.getZone() == null, is(true));
        assertThat(zoned.getZone(), is("Europe/London"));
        assertThat(zoned.getExpected(), is("now"));
    }

    @Test
    public void shouldRejectAnInvalidMatcherAsSoonAsItIsBuilt() {
        assertThat(assertThrows(IllegalArgumentException.class, () -> after("tomorrow")).getMessage(), containsString("cannot read expected \"tomorrow\""));
        assertThat(assertThrows(IllegalArgumentException.class, () -> after("now").withZone("Mars/Olympus")).getMessage(), containsString("unknown zone"));
        assertThat(assertThrows(IllegalArgumentException.class, () -> after("now").withActualFormat("yyyy{")).getMessage(), containsString("invalid actualFormat pattern"));
    }

    @Test
    public void shouldSerialiseAsTheObjectForm() {
        String json = serializer.serialize(expectation());
        assertThat(json, containsString("\"If-Modified-Since\" : [ {\n        \"before\" : \"now\",\n        \"actualFormat\" : \"rfc1123\"\n      } ]"));
        assertThat(json, containsString("\"before\" : \"now\""));
        assertThat(json, containsString("\"after\" : \"2026-01-01\""));
        assertThat(json, containsString("\"zone\" : \"Europe/London\""));
        assertThat(json, containsString("\"not\" : true"));
        assertThat(json, containsString("\"equalToDateTime\" : \"now\""));
        assertThat(json, containsString("\"applyTruncationLast\" : false"));
    }

    @Test
    public void shouldRoundTripThroughExpectationJson() {
        Expectation expectation = expectation();
        String json = serializer.serialize(expectation);

        Expectation roundTripped = serializer.deserialize(json);

        assertThat(roundTripped, is(expectation));
        assertThat(serializer.serialize(roundTripped), is(json));
        NottableString cookie = ((HttpRequest) roundTripped.getHttpRequest()).getCookies().getEntries().get(0).getValue();
        assertThat(cookie, instanceOf(NottableDateTimeString.class));
        assertThat(cookie.isNot(), is(true));
        assertThat(((NottableDateTimeString) cookie).getTruncateActual(), is("startOfDay"));
    }

    @Test
    public void shouldRejectInvalidJsonWithAReason() {
        String invalidExpected = "{ \"httpRequest\": { \"headers\": { \"X\": [ { \"after\": \"tomorrow\" } ] } }, \"httpResponse\": {} }";
        assertThat(assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(invalidExpected)).getMessage(), containsString("cannot read expected \"tomorrow\""));

        String optionWithoutOperator = "{ \"httpRequest\": { \"headers\": { \"X\": [ { \"value\": \"x\", \"zone\": \"UTC\" } ] } }, \"httpResponse\": {} }";
        assertThat(assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(optionWithoutOperator)).getMessage(), containsString("need one of before, after or equalToDateTime"));

        String twoOperators = "{ \"httpRequest\": { \"headers\": { \"X\": [ { \"after\": \"now\", \"before\": \"now\" } ] } }, \"httpResponse\": {} }";
        assertThat(assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(twoOperators)).getMessage(), containsString("only one of before, after or equalToDateTime"));

        String unknownField = "{ \"httpRequest\": { \"headers\": { \"X\": [ { \"after\": \"now\", \"colour\": \"blue\" } ] } }, \"httpResponse\": {} }";
        assertThat(assertThrows(IllegalArgumentException.class, () -> serializer.deserialize(unknownField)).getMessage(), containsString("colour"));
    }

    @Test
    public void shouldGenerateJavaCodeThatBuildsTheSameMatcher() {
        String java = new ExpectationToJavaSerializer().serialize(1, expectation());
        assertThat(java, containsString("new Header(string(\"If-Modified-Since\"), NottableDateTimeString.before(\"now\").withActualFormat(\"rfc1123\"))"));
        assertThat(java, containsString("new Parameter(string(\"from\"), NottableDateTimeString.after(\"2026-01-01\").withActualFormat(\"dd/MM/yyyy\").withZone(\"Europe/London\"))"));
        assertThat(java, containsString("new Cookie(string(\"seen\"), NottableDateTimeString.equalToDateTime(\"now\").withTruncateExpected(\"startOfDay\").withTruncateActual(\"startOfDay\").withApplyTruncationLast(false).withNot(true))"));
    }

    @Test
    public void shouldAcceptAndRoundTripTheSharedClientFixture() throws Exception {
        // the cross-language fidelity fixture every client round-trips; the server must accept it as written
        String json = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("../../test-fixtures/expectations/matcher_request_date_time.json")), java.nio.charset.StandardCharsets.UTF_8);
        Expectation expectation = serializer.deserialize(json);
        HttpRequest request = (HttpRequest) expectation.getHttpRequest();

        assertThat(request.getHeaders().getValues(string("X-Issued-At")).iterator().next(), is(after("now -1 hours")));
        assertThat(request.getHeaders().getValues(string("X-Plain")).iterator().next().getClass(), is((Object) NottableString.class));
        assertThat(request.getCookies().getEntries().get(0).getValue(), is(equalToDateTime("now +1 days").withTruncateExpected("first day of month").withTruncateActual("startOfDay").withApplyTruncationLast(true).withNot(true)));
        assertThat(serializer.deserialize(serializer.serialize(expectation)), is(expectation));
    }
}
