package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;

import java.net.InetAddress;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.Not.not;

public class HttpRequestPropertiesMatcherClientIpTest {

    private final Configuration configuration = configuration();
    private final MockServerLogger mockServerLogger = new MockServerLogger(HttpRequestPropertiesMatcherClientIpTest.class);

    @Test
    public void shouldMatchOnlyRequestsFromTheClientIpRange() throws Exception {
        HttpRequestPropertiesMatcher matcher = dataPlane(request().withPath("/api").withClientIp("10.0.0.0/8"));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(true));
        assertThat(matcher.matches(null, received("/api", "192.168.1.1")), is(false));
        assertThat(matcher.matches(null, received("/other", "10.1.2.3")), is(false));
    }

    @Test
    public void shouldMatchNegatedClientIp() throws Exception {
        HttpRequestPropertiesMatcher matcher = dataPlane(request().withPath("/api").withClientIp("!10.0.0.0/8"));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(false));
        assertThat(matcher.matches(null, received("/api", "192.168.1.1")), is(true));
    }

    @Test
    public void shouldApplyRequestLevelNotToClientIp() throws Exception {
        HttpRequestPropertiesMatcher matcher = dataPlane(not(request().withPath("/api").withClientIp("10.0.0.0/8")));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(false));
        assertThat(matcher.matches(null, received("/api", "192.168.1.1")), is(true));
    }

    @Test
    public void shouldIgnoreClientIpWhenTheExpectationHasNone() throws Exception {
        HttpRequestPropertiesMatcher matcher = dataPlane(request().withPath("/api"));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(true));
        assertThat(matcher.matches(null, request().withPath("/api")), is(true));
    }

    @Test
    public void shouldReportTheClientIpDifferenceWithoutMarkingItDecidedByMethodOrPath() throws Exception {
        HttpRequestPropertiesMatcher matcher = dataPlane(request().withPath("/api").withClientIp("10.0.0.0/8"));
        HttpRequest request = received("/api", "192.168.1.1").withRemoteAddress("192.168.1.1:5000");
        MatchDifference context = new MatchDifference(true, request);
        assertThat(matcher.matches(context, request), is(false));
        assertThat(context.getAllDifferences().keySet(), contains(MatchDifference.Field.CLIENT_IP));
        assertThat(String.valueOf(context.getDifferences(MatchDifference.Field.CLIENT_IP)), containsString("192.168.1.1:5000"));
        // the closest-match memo reuses a non-match for every request with the same method and path only when
        // this flag is set, and two requests with the same method and path can differ by client address
        assertThat(context.isDecidedByMethodOrPath(), is(false));
    }

    @Test
    public void shouldFilterRecordedRequestsOnTheControlPlane() throws Exception {
        HttpRequestPropertiesMatcher matcher = controlPlane(request().withClientIp("127.0.0.0/8"));
        assertThat(matcher.matches(null, received("/api", "127.0.0.1")), is(true));
        assertThat(matcher.matches(null, received("/api", "10.0.0.1")), is(false));
        // a recorded request read back from JSON carries only the text form of its remote address
        assertThat(matcher.matches(null, request().withPath("/api").withRemoteAddress("127.0.0.1:61000")), is(true));
    }

    @Test
    public void shouldRebuildTheCompiledClientIpWhenTheExpectationChanges() throws Exception {
        HttpRequestPropertiesMatcher matcher = dataPlane(request().withPath("/api").withClientIp("10.0.0.0/8"));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(true));
        matcher.update(new Expectation(request().withPath("/api").withClientIp("192.168.0.0/16")));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(false));
        assertThat(matcher.matches(null, received("/api", "192.168.4.4")), is(true));
        matcher.update(new Expectation(request().withPath("/api")));
        assertThat(matcher.matches(null, received("/api", "10.1.2.3")), is(true));
    }

    @Test
    public void shouldTreatClientIpAsPartOfExpectationIdentity() {
        assertThat(request().withClientIp("10.0.0.0/8").equals(request().withClientIp("10.0.0.0/8")), is(true));
        assertThat(request().withClientIp("10.0.0.0/8").hashCode(), is(request().withClientIp("10.0.0.0/8").hashCode()));
        assertThat(request().withClientIp("10.0.0.0/8").equals(request().withClientIp("!10.0.0.0/8")), is(false));
        assertThat(request().withClientIp("10.0.0.0/8").equals(request()), is(false));
        // a request without a client IP keeps the hash it had before the field existed
        assertThat(request().withPath("/a").withClientIp((String) null).hashCode(), is(request().withPath("/a").hashCode()));
        HttpRequest original = request().withPath("/a").withClientIp("10.0.0.0/8").withRemoteAddress("10.0.0.1:1");
        assertThat(original.clone(), is(original));
        assertThat(original.clone().getClientIp(), is(original.getClientIp()));
        assertThat(original.shallowClone().getClientIp(), is(original.getClientIp()));
    }

    @Test
    public void shouldKeepTheTransportsAddressBytesWhenCloned() throws Exception {
        HttpRequest original = received("/api", "10.1.2.3");
        assertThat(original.clone().getRemoteAddressBytes() == original.getRemoteAddressBytes(), is(true));
        assertThat(original.shallowClone().getRemoteAddressBytes() == original.getRemoteAddressBytes(), is(true));
        assertThat(original.withRemoteAddress("10.9.9.9:1").getRemoteAddressBytes(), is(nullValue()));
    }

    private HttpRequestPropertiesMatcher dataPlane(RequestDefinition requestDefinition) {
        HttpRequestPropertiesMatcher matcher = new HttpRequestPropertiesMatcher(configuration, mockServerLogger);
        matcher.update(new Expectation(requestDefinition));
        return matcher;
    }

    private HttpRequestPropertiesMatcher controlPlane(RequestDefinition requestDefinition) {
        HttpRequestPropertiesMatcher matcher = new HttpRequestPropertiesMatcher(configuration, mockServerLogger).withControlPlaneMatcher(true);
        matcher.update(requestDefinition);
        return matcher;
    }

    static HttpRequest received(String path, String peer) throws Exception {
        return request().withMethod("GET").withPath(path).withReceivedRemoteAddress(new HttpRequest.RemoteAddress(peer + ":40000", InetAddress.getByName(peer)));
    }
}
