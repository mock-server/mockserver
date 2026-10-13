package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.HttpRequestMatcher;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.DnsRecordType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Not;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.HttpRequestSerializer;
import org.mockserver.serialization.RequestDefinitionSerializer;

import java.util.List;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.BinaryRequestDefinition.binaryRequest;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.DnsRequestDefinition.dnsRequest;
import static org.mockserver.model.DnsResponse.dnsResponse;
import static org.mockserver.model.GrpcStreamResponse.grpcStreamResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Coverage for {@link RequestMatchers#retrieveActiveExpectations(org.mockserver.model.RequestDefinition)}
 * and {@link RequestMatchers#retrieveRequestMatchers(org.mockserver.model.RequestDefinition)} — the
 * <em>filter/reverse</em>-matching listings that feed the "active expectations" views (the dashboard
 * WebSocket push behind the Mocks page, and {@code PUT /mockserver/retrieve?type=ACTIVE_EXPECTATIONS}).
 *
 * <p>These pin a regression where DNS (and binary) expectations were silently dropped from those
 * listings. Both feeds supply an HTTP-shaped filter — and when "show all" is requested they send an
 * <em>empty</em> {@code request()} rather than {@code null}. An HTTP filter cannot describe a
 * {@link org.mockserver.model.DnsRequestDefinition} / {@link org.mockserver.model.BinaryRequestDefinition},
 * so reverse-matching it against them always failed and excluded them — meaning DNS mocks never
 * appeared on the Mocks page even though they were created successfully (HTTP-shaped gRPC mocks, by
 * contrast, were unaffected, which is why the bug looked DNS-specific). Non-HTTP protocol
 * expectations now bypass an HTTP/OpenAPI filter and are always listed.
 */
public class RequestMatchersActiveRetrievalTest {

    private RequestMatchers requestMatchers;

    @Before
    public void setup() {
        requestMatchers = new RequestMatchers(
            configuration(), new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
    }

    private List<String> ids(List<Expectation> expectations) {
        return expectations.stream().map(Expectation::getId).collect(Collectors.toList());
    }

    @Test
    public void emptyHttpFilterListsDnsExpectation() {
        // Reproduces the reported bug: the REST retrieve endpoint substitutes a blank body with an
        // empty request() (NOT null), so this empty-HTTP-filter path must still surface DNS mocks.
        requestMatchers.add(new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1")
            .thenRespondWithDns(dnsResponse()), API);

        List<Expectation> active = requestMatchers.retrieveActiveExpectations(request());

        assertThat(ids(active), contains("dns-1"));
    }

    @Test
    public void nullFilterListsDnsExpectation() {
        // The internal "no filter" path (WebSocket push context, metrics, export) passes null.
        requestMatchers.add(new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1")
            .thenRespondWithDns(dnsResponse()), API);

        assertThat(ids(requestMatchers.retrieveActiveExpectations(null)), contains("dns-1"));
    }

    @Test
    public void emptyHttpFilterListsBinaryExpectation() {
        // Binary expectations share the same non-HTTP RequestDefinition shape as DNS.
        requestMatchers.add(new Expectation(binaryRequest("ping".getBytes())).withId("bin-1")
            .thenRespondWithBinary(binaryResponse()), API);

        assertThat(ids(requestMatchers.retrieveActiveExpectations(request())), contains("bin-1"));
    }

    @Test
    public void emptyHttpFilterListsMixedProtocolExpectations() {
        // The Mocks page shows everything at once: HTTP, gRPC (HTTP-shaped), DNS and binary must all
        // be returned by the default empty-filter listing.
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1")
            .thenRespond(response().withStatusCode(200)), API);
        requestMatchers.add(new Expectation(request().withMethod("POST").withPath("/greeter.v1/SayHello")).withId("grpc-1")
            .thenRespondWithGrpcStream(grpcStreamResponse()), API);
        requestMatchers.add(new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1")
            .thenRespondWithDns(dnsResponse()), API);
        requestMatchers.add(new Expectation(binaryRequest("ping".getBytes())).withId("bin-1")
            .thenRespondWithBinary(binaryResponse()), API);

        assertThat(ids(requestMatchers.retrieveActiveExpectations(request())),
            containsInAnyOrder("http-1", "grpc-1", "dns-1", "bin-1"));
    }

    @Test
    public void specificHttpFilterStillNarrowsHttpButKeepsDns() {
        // A concrete HTTP path filter must still exclude non-matching HTTP stubs (filter semantics
        // preserved), while non-HTTP protocol mocks the filter cannot address remain listed.
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/wanted")).withId("http-wanted")
            .thenRespond(response().withStatusCode(200)), API);
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/other")).withId("http-other")
            .thenRespond(response().withStatusCode(200)), API);
        requestMatchers.add(new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1")
            .thenRespondWithDns(dnsResponse()), API);

        List<String> active = ids(requestMatchers.retrieveActiveExpectations(request().withPath("/wanted")));

        assertThat(active, hasItem("http-wanted"));
        assertThat(active, hasItem("dns-1"));
        assertThat(active, not(hasItem("http-other")));
    }

    @Test
    public void retrieveRequestMatchersWithEmptyHttpFilterListsDns() {
        // The dashboard WebSocket feed (Mocks page) uses retrieveRequestMatchers with the UI's
        // HTTP-shaped filter — empty when no filter is typed. It must include the DNS matcher.
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1")
            .thenRespond(response().withStatusCode(200)), API);
        requestMatchers.add(new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1")
            .thenRespondWithDns(dnsResponse()), API);

        List<String> matcherIds = requestMatchers.retrieveRequestMatchers(request()).stream()
            .map(HttpRequestMatcher::getExpectation)
            .map(Expectation::getId)
            .collect(Collectors.toList());

        assertThat(matcherIds, hasSize(2));
        assertThat(matcherIds, containsInAnyOrder("http-1", "dns-1"));
    }

    @Test
    public void dnsFilterStillNarrowsToDnsExpectations() {
        // When the filter IS a DNS request (same protocol as the expectation), normal reverse
        // matching applies and HTTP expectations are not force-included.
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1")
            .thenRespond(response().withStatusCode(200)), API);
        requestMatchers.add(new Expectation(dnsRequest("api.example.com", DnsRecordType.A)).withId("dns-1")
            .thenRespondWithDns(dnsResponse()), API);

        List<String> active = ids(requestMatchers.retrieveActiveExpectations(dnsRequest("api.example.com", DnsRecordType.A)));

        assertThat(active, contains("dns-1"));
        assertThat(active, not(hasItem("http-1")));
    }

    @Test
    public void emptyHttpFilterListsNotExpectation() {
        // A not(...) definition fails the reverse match against an empty filter, so the "show all"
        // listing dropped it although it serves requests.
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1")
            .thenRespond(response()), API);
        requestMatchers.add(new Expectation(Not.not(request().withPath("/secret"))).withId("not-1")
            .thenRespond(response()), API);

        assertThat(ids(requestMatchers.retrieveActiveExpectations(request())), containsInAnyOrder("http-1", "not-1"));
        assertThat(ids(requestMatchers.retrieveActiveExpectations(request().withLogCorrelationId("some-correlation-id"))), containsInAnyOrder("http-1", "not-1"));
        assertThat(ids(requestMatchers.retrieveActiveExpectations(new RequestDefinitionSerializer(new MockServerLogger()).deserialize("{}"))), containsInAnyOrder("http-1", "not-1"));
    }

    @Test
    public void retrieveRequestMatchersWithEmptyHttpFilterListsNotExpectation() {
        // the dashboard's Active Expectations panel with no filter typed; the UI sends {}
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1")
            .thenRespond(response()), API);
        requestMatchers.add(new Expectation(Not.not(request().withPath("/secret"))).withId("not-1")
            .thenRespond(response()), API);

        for (HttpRequest filter : new HttpRequest[]{request(), new HttpRequestSerializer(new MockServerLogger()).deserialize("{}")}) {
            List<String> matcherIds = requestMatchers.retrieveRequestMatchers(filter).stream()
                .map(HttpRequestMatcher::getExpectation)
                .map(Expectation::getId)
                .collect(Collectors.toList());

            assertThat(matcherIds, containsInAnyOrder("http-1", "not-1"));
        }
    }

    @Test
    public void nonEmptyHttpFilterStillReverseMatchesNotExpectation() {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/api/users")).withId("http-1")
            .thenRespond(response()), API);
        requestMatchers.add(new Expectation(Not.not(request().withPath("/secret"))).withId("not-1")
            .thenRespond(response()), API);

        assertThat(ids(requestMatchers.retrieveActiveExpectations(request().withMethod("GET"))), contains("http-1"));
        assertThat(ids(requestMatchers.retrieveActiveExpectations(Not.not(request()))), not(hasItem("http-1")));
    }

    @Test
    public void emptyHttpFilterStillLeavesOutInactiveExpectations() {
        requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/once"), Times.once(), TimeToLive.unlimited(), 0).withId("once")
            .thenRespond(response()), API);
        requestMatchers.add(new Expectation(Not.not(request().withPath("/secret"))).withId("not-1")
            .thenRespond(response()), API);
        requestMatchers.firstMatchingExpectation(request().withMethod("GET").withPath("/once"));
        // its response has been written
        requestMatchers.retrieveRequestMatchers(null).stream()
            .filter(matcher -> "once".equals(matcher.getExpectation().getId()))
            .forEach(matcher -> matcher.setResponseInProgress(false));

        assertThat(ids(requestMatchers.retrieveActiveExpectations(request())), contains("not-1"));
    }
}
