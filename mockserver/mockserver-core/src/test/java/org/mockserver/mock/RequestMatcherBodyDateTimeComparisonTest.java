package org.mockserver.mock;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.Body;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ExpectationSerializer;
import org.mockserver.time.FixedTime;
import org.mockserver.time.TimeService;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.JsonPathBody.jsonPath;
import static org.mockserver.model.Not.not;
import static org.mockserver.model.NottableDateTimeString.after;
import static org.mockserver.model.NottableDateTimeString.before;
import static org.mockserver.model.NottableDateTimeString.equalToDateTime;
import static org.mockserver.model.XPathBody.xpath;

/**
 * Date-time comparisons on the value a JSONPath or XPath body matcher selects, routed through the
 * {@link RequestMatchers} engine as a running server would, built both in Java and from JSON.
 */
public class RequestMatcherBodyDateTimeComparisonTest {

    @ClassRule
    public static final FixedTime fixedTime = new FixedTime();

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        requestMatchers = new RequestMatchers(configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
    }

    private static String minutesFromNow(long minutes) {
        return TimeService.FIXED_INSTANT_FOR_TESTS.plus(Duration.ofMinutes(minutes)).toString();
    }

    private Expectation add(Body<?> body) {
        Expectation expectation = new Expectation(request().withPath("/orders").withBody(body)).thenRespond(response().withBody("matched"));
        requestMatchers.add(expectation, API);
        return expectation;
    }

    private Expectation matching(String body) {
        return requestMatchers.firstMatchingExpectation(request().withPath("/orders").withBody(body));
    }

    private static String order(String created) {
        return "{\"order\": {\"id\": 7, \"created\": \"" + created + "\"}}";
    }

    private static String xmlOrder(String created) {
        return "<order id=\"7\"><created>" + created + "</created></order>";
    }

    // JSONPath

    @Test
    public void shouldMatchJsonPathValueAfterNowWithOffset() {
        Expectation expectation = add(jsonPath("$.order.created", after("now -1 hours")));

        assertThat(matching(order(minutesFromNow(-30))), is(expectation));
        assertThat(matching(order(minutesFromNow(-90))), is(nullValue()));
    }

    @Test
    public void shouldMatchJsonPathValueBeforeLiteral() {
        Expectation expectation = add(jsonPath("$.order.created", before("2026-10-21T00:00:00Z")));

        assertThat(matching(order("2026-10-20T23:59:59Z")), is(expectation));
        assertThat(matching(order("2026-10-21T00:00:00Z")), is(nullValue()));
        assertThat(matching(order("2026-10-21T00:00:01Z")), is(nullValue()));
    }

    @Test
    public void shouldMatchJsonPathValueEqualToDateTimeWithTruncationAndFormat() {
        Expectation expectation = add(jsonPath("$.order.created", equalToDateTime("2026-10-21").withActualFormat("dd/MM/yyyy HH:mm").withTruncateActual("startOfDay")));

        assertThat(matching(order("21/10/2026 15:30")), is(expectation));
        assertThat(matching(order("22/10/2026 00:00")), is(nullValue()));
        assertThat(matching(order("2026-10-21T00:00:00Z")), is(nullValue()));
    }

    @Test
    public void shouldNotMatchJsonPathValueThatIsNotADateTimeOrIsNotSelected() {
        add(jsonPath("$.order.created", after("2000-01-01T00:00:00Z")));

        assertThat(matching(order("yesterday")), is(nullValue()));
        assertThat(matching("{\"order\": {\"id\": 7}}"), is(nullValue()));
        assertThat(matching("{\"order\": {\"created\": true}}"), is(nullValue()));
        assertThat(matching("{\"order\": {\"created\": {\"at\": \"2026-10-21T00:00:00Z\"}}}"), is(nullValue()));
        assertThat(matching("not json"), is(nullValue()));
    }

    @Test
    public void shouldMatchJsonPathWholeNumberAsEpoch() {
        Expectation expectation = add(jsonPath("$.order.created", after("2026-01-01T00:00:00Z")));

        assertThat(matching("{\"order\": {\"created\": 1792569600}}"), is(expectation));
        assertThat(matching("{\"order\": {\"created\": 1792569600000}}"), is(expectation));
        assertThat(matching("{\"order\": {\"created\": 1700000000}}"), is(nullValue()));
        assertThat(matching("{\"order\": {\"created\": 1792569600.5}}"), is(nullValue()));
    }

    @Test
    public void shouldMatchWhenAnyValueAnIndefiniteJsonPathSelectsMatches() {
        Expectation expectation = add(jsonPath("$.items[*].expires", after("now")));

        assertThat(matching("{\"items\": [{\"expires\": \"" + minutesFromNow(-5) + "\"}, {\"expires\": \"" + minutesFromNow(5) + "\"}]}"), is(expectation));
        assertThat(matching("{\"items\": [{\"expires\": \"" + minutesFromNow(-5) + "\"}, {\"expires\": \"never\"}]}"), is(nullValue()));
        assertThat(matching("{\"items\": []}"), is(nullValue()));
    }

    @Test
    public void shouldNegateTheComparisonButNotAnUnreadableValue() {
        Expectation expectation = add(jsonPath("$.order.created", before("now").withNot(true)));

        assertThat(matching(order(minutesFromNow(5))), is(expectation));
        assertThat(matching(order(minutesFromNow(-5))), is(nullValue()));
        assertThat(matching(order("soon")), is(nullValue()));
    }

    @Test
    public void shouldNegateTheWholeBodyMatcher() {
        Expectation expectation = add(not(jsonPath("$.order.created", before("now"))));

        assertThat(matching(order(minutesFromNow(5))), is(expectation));
        assertThat(matching(order("soon")), is(expectation));
        assertThat(matching(order(minutesFromNow(-5))), is(nullValue()));
    }

    // XPath

    @Test
    public void shouldMatchXPathNodeAfterNowWithOffset() {
        Expectation expectation = add(xpath("/order/created", after("now -1 days")));

        assertThat(matching(xmlOrder(minutesFromNow(-60))), is(expectation));
        assertThat(matching(xmlOrder(minutesFromNow(-60 * 25))), is(nullValue()));
    }

    @Test
    public void shouldMatchXPathAttributeBeforeLiteral() {
        Expectation expectation = add(xpath("/order/@expires", before("2026-10-21T00:00:00Z").withActualFormat("rfc1123")));

        assertThat(matching("<order expires=\"Tue, 20 Oct 2026 08:00:00 GMT\"/>"), is(expectation));
        assertThat(matching("<order expires=\"Thu, 22 Oct 2026 08:00:00 GMT\"/>"), is(nullValue()));
        assertThat(matching("<order expires=\"2026-10-20T08:00:00Z\"/>"), is(nullValue()));
    }

    @Test
    public void shouldMatchXPathNodeEqualToDateTimeInAZone() {
        Expectation expectation = add(xpath("/order/created", equalToDateTime("2026-10-21T14:00:00Z").withZone("Europe/London")));

        assertThat(matching(xmlOrder("2026-10-21T15:00:00")), is(expectation));
        assertThat(matching(xmlOrder("2026-10-21T14:00:00")), is(nullValue()));
    }

    @Test
    public void shouldMatchWhenAnyNodeTheXPathSelectsMatches() {
        Expectation expectation = add(xpath("//item/expires", after("now")));

        assertThat(matching("<items><item><expires>" + minutesFromNow(-5) + "</expires></item><item><expires>" + minutesFromNow(5) + "</expires></item></items>"), is(expectation));
        assertThat(matching("<items><item><expires>" + minutesFromNow(-5) + "</expires></item></items>"), is(nullValue()));
        assertThat(matching("<items/>"), is(nullValue()));
    }

    @Test
    public void shouldNotMatchXPathThatDoesNotSelectNodesOrBodyThatIsNotXml() {
        add(xpath("string(/order/created)", after("2000-01-01T00:00:00Z")));

        assertThat(matching(xmlOrder("2026-10-21T00:00:00Z")), is(nullValue()));
        assertThat(matching("not xml"), is(nullValue()));
    }

    @Test
    public void shouldNegateXPathComparison() {
        Expectation expectation = add(xpath("/order/created", after("now").withNot(true)));

        assertThat(matching(xmlOrder(minutesFromNow(-5))), is(expectation));
        assertThat(matching(xmlOrder(minutesFromNow(5))), is(nullValue()));
        assertThat(matching(xmlOrder("whenever")), is(nullValue()));
    }

    // no date-time comparison: the old meaning

    @Test
    public void shouldKeepPlainJsonPathAndXPathMeaning() {
        Expectation jsonExpectation = add(jsonPath("$.order.created"));
        assertThat(matching(order("yesterday")), is(jsonExpectation));
        assertThat(matching("{\"order\": {}}"), is(nullValue()));

        requestMatchers.reset();
        Expectation xmlExpectation = add(xpath("/order/created"));
        assertThat(matching(xmlOrder("whenever")), is(xmlExpectation));
        assertThat(matching("<order/>"), is(nullValue()));
    }

    @Test
    public void shouldTreatBodiesWithAndWithoutADateTimeAsDifferentMatchers() {
        assertThat(jsonPath("$.a").equals(jsonPath("$.a", after("now"))), is(false));
        assertThat(jsonPath("$.a", after("now")).equals(jsonPath("$.a", after("now"))), is(true));
        assertThat(jsonPath("$.a", after("now")).equals(jsonPath("$.a", before("now"))), is(false));
        assertThat(xpath("/a").equals(xpath("/a", after("now"))), is(false));
        assertThat(xpath("/a", after("now")).hashCode(), is(xpath("/a", after("now")).hashCode()));
        assertThat(jsonPath("$.a").hashCode(), is(jsonPath("$.a").hashCode()));
    }

    // JSON

    @Test
    public void shouldMatchExpectationsWrittenInJson() {
        Expectation jsonExpectation = deserialize("{ \"type\": \"JSON_PATH\", \"jsonPath\": \"$.order.created\", \"dateTime\": { \"after\": \"now -1 hours\" } }");
        requestMatchers.add(jsonExpectation, API);
        assertThat(matching(order(minutesFromNow(-30))), is(jsonExpectation));
        assertThat(matching(order(minutesFromNow(-90))), is(nullValue()));

        requestMatchers.reset();
        Expectation xmlExpectation = deserialize("{ \"type\": \"XPATH\", \"xpath\": \"/order/created\", \"dateTime\": { \"not\": true, \"before\": \"2026-01-01\", \"actualFormat\": \"iso\", \"zone\": \"UTC\" } }");
        requestMatchers.add(xmlExpectation, API);
        assertThat(matching(xmlOrder("2026-03-01T00:00:00Z")), is(xmlExpectation));
        assertThat(matching(xmlOrder("2025-03-01T00:00:00Z")), is(nullValue()));
    }

    @Test
    public void shouldRoundTripABodyDateTimeThroughJson() {
        ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());
        Expectation original = new Expectation(request().withPath("/orders").withBody(jsonPath("$.created", before("now +2 days").withTruncateExpected("startOfDay").withApplyTruncationLast(true))))
            .thenRespond(response().withBody("matched"));

        String json = serializer.serialize(original);

        assertThat(json, containsString("\"dateTime\" : {"));
        assertThat(json, containsString("\"before\" : \"now +2 days\""));
        assertThat(serializer.deserialize(json), is(original));
    }

    @Test
    public void shouldRejectAnInvalidBodyDateTime() {
        assertThat(rejection("{ \"type\": \"JSON_PATH\", \"jsonPath\": \"$.a\", \"dateTime\": { \"after\": \"next tuesday\" } }"), containsString("cannot read expected \"next tuesday\""));
        assertThat(rejection("{ \"type\": \"JSON_PATH\", \"jsonPath\": \"$.a\", \"dateTime\": { \"zone\": \"UTC\" } }"), containsString("dateTime"));
        assertThat(rejection("{ \"type\": \"JSON_PATH\", \"jsonPath\": \"$.a\", \"dateTime\": { \"after\": \"now\", \"before\": \"now\" } }"), containsString("dateTime"));
        assertThat(rejection("{ \"type\": \"XPATH\", \"xpath\": \"/a\", \"dateTime\": \"now\" }"), containsString("dateTime"));
        assertThat(rejection("{ \"type\": \"REGEX\", \"regex\": \".*\", \"dateTime\": { \"after\": \"now\" } }"), containsString("dateTime"));
    }

    @Test
    public void shouldKeepAnUntypedJsonBodyHoldingADateTimeFieldAsAJsonBodyMatcher() {
        // the untyped shorthand is a JSON body matcher; a "dateTime" or "datetime" key in it is body content
        Expectation stringValue = deserialize("{ \"id\": 1, \"dateTime\": \"2026-01-01T00:00:00Z\" }");
        assertThat(((HttpRequest) stringValue.getHttpRequest()).getBody(), instanceOf(org.mockserver.model.JsonBody.class));
        requestMatchers.add(stringValue, API);
        assertThat(matching("{\"id\": 1, \"dateTime\": \"2026-01-01T00:00:00Z\", \"extra\": true}"), is(stringValue));
        assertThat(matching("{\"id\": 1, \"dateTime\": \"2027-01-01T00:00:00Z\"}"), is(nullValue()));

        requestMatchers.reset();
        Expectation objectValue = deserialize("{ \"id\": 2, \"datetime\": { \"after\": \"now\" } }");
        requestMatchers.add(objectValue, API);
        assertThat(matching("{\"id\": 2, \"datetime\": {\"after\": \"now\"}}"), is(objectValue));
        assertThat(matching("{\"id\": 2, \"datetime\": {\"after\": \"later\"}}"), is(nullValue()));
    }

    @Test
    public void shouldNotMatchABodyThatIsTheExpressionTextItselfWhenComparingDateTimes() {
        add(jsonPath("$.order.created", after("2000-01-01T00:00:00Z")));
        assertThat(matching("$.order.created"), is(nullValue()));

        requestMatchers.reset();
        add(xpath("/order/created", after("2000-01-01T00:00:00Z")));
        assertThat(matching("/order/created"), is(nullValue()));

        requestMatchers.reset();
        Expectation plain = add(jsonPath("$.order.created"));
        assertThat(matching("$.order.created"), is(plain));
    }

    @Test
    public void shouldAcceptAndRoundTripTheSharedClientFixtures() throws Exception {
        ExpectationSerializer serializer = new ExpectationSerializer(new MockServerLogger());
        Expectation jsonPathFixture = serializer.deserialize(fixture("matcher_body_json_path_date_time.json"));
        Expectation xpathFixture = serializer.deserialize(fixture("matcher_body_xpath_date_time.json"));

        assertThat(((HttpRequest) jsonPathFixture.getHttpRequest()).getBody(), is(jsonPath("$.order.created", after("now -1 days").withActualFormat("iso").withTruncateExpected("startOfDay").withZone("Europe/London"))));
        assertThat(((HttpRequest) xpathFixture.getHttpRequest()).getBody(), is(xpath("/order/@expires", before("2026-01-01T00:00:00Z").withActualFormat("rfc1123").withTruncateActual("first day of month").withApplyTruncationLast(true).withNot(true))));
        assertThat(serializer.deserialize(serializer.serialize(jsonPathFixture)), is(jsonPathFixture));
        assertThat(serializer.deserialize(serializer.serialize(xpathFixture)), is(xpathFixture));
    }

    // the cross-language fidelity fixtures every client round-trips; the server must accept them as written
    private static String fixture(String name) throws java.io.IOException {
        return new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get("../../test-fixtures/expectations/" + name)), java.nio.charset.StandardCharsets.UTF_8);
    }

    private static Expectation deserialize(String body) {
        return new ExpectationSerializer(new MockServerLogger()).deserialize("{ \"httpRequest\": { \"path\": \"/orders\", \"body\": " + body + " }, \"httpResponse\": { \"body\": \"matched\" } }");
    }

    private static String rejection(String body) {
        return assertThrows(IllegalArgumentException.class, () -> deserialize(body)).getMessage();
    }
}
