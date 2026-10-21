package org.mockserver.openapi;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.HttpState;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.HttpRequest;
import org.mockserver.persistence.ExpectationFileSystemPersistence;
import org.mockserver.responsewriter.ResponseWriter;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.server.initialize.ExpectationInitializerLoader;
import org.mockserver.state.InMemoryBlobStore;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * A server whose own configuration turns forwardProxyBlockPrivateNetworks on refuses a spec at a private address on
 * every path that turns an OpenAPI expectation into expectations, while the global property stays off. Each test
 * uses its own spec path, so no parse is answered from the parser's cache.
 */
public class OpenAPISpecFetchServerSettingTest {

    private static final String SPEC = "{\"openapi\":\"3.0.0\",\"info\":{\"title\":\"pets\",\"version\":\"1\"},"
        + "\"paths\":{\"/pets\":{\"get\":{\"operationId\":\"listPets\",\"responses\":{\"200\":{\"description\":\"ok\"}}}}}}";

    private final AtomicInteger specRequests = new AtomicInteger();
    private final String specPath = "/" + UUID.randomUUID() + "/spec.json";
    private final List<HttpState> httpStates = new ArrayList<>();
    private HttpServer server;

    @Before
    public void startSpecServer() throws IOException {
        assertThat("the global property must be off for these tests to mean anything", ConfigurationProperties.forwardProxyBlockPrivateNetworks(), is(false));
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("localhost"), 0), 0);
        server.createContext(specPath, this::answer);
        server.start();
    }

    @After
    public void stop() {
        server.stop(0);
        httpStates.forEach(HttpState::stop);
    }

    private void answer(HttpExchange exchange) throws IOException {
        specRequests.incrementAndGet();
        byte[] body = SPEC.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
        exchange.close();
    }

    private String openAPIExpectationJson() {
        return "[{\"specUrlOrPayload\":\"http://127.0.0.1:" + server.getAddress().getPort() + specPath + "\"}]";
    }

    private static Configuration blocking() {
        return configuration().forwardProxyBlockPrivateNetworks(true);
    }

    private HttpState httpState(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, OpenAPISpecFetchServerSettingTest.class);
        HttpState httpState = new HttpState(configuration, logger, new Scheduler(configuration, logger, true));
        httpStates.add(httpState);
        return httpState;
    }

    private RequestMatchers requestMatchers(Configuration configuration) {
        MockServerLogger logger = new MockServerLogger(configuration, OpenAPISpecFetchServerSettingTest.class);
        return new RequestMatchers(configuration, logger, new Scheduler(configuration, logger, true), new WebSocketClientRegistry(configuration, logger));
    }

    private File file(String contents) throws IOException {
        File file = File.createTempFile("openAPISpecFetch", ".json");
        file.deleteOnExit();
        Files.write(file.toPath(), contents.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    @Test
    public void putExpectationIsRefusedASpecAtAPrivateAddress() {
        HttpState httpState = httpState(blocking());
        HttpRequest put = request("/mockserver/expectation").withMethod("PUT").withBody(openAPIExpectationJson());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> httpState.handle(put, mock(ResponseWriter.class), false));

        assertThat(refused.getMessage(), containsString("loopback"));
        assertThat(specRequests.get(), is(0));
        assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null), is(empty()));
    }

    @Test
    public void putExpectationFetchesASpecAtAPrivateAddressWithTheSettingOff() {
        HttpState httpState = httpState(configuration().forwardProxyBlockPrivateNetworks(false));
        HttpRequest put = request("/mockserver/expectation").withMethod("PUT").withBody(openAPIExpectationJson());

        httpState.handle(put, mock(ResponseWriter.class), false);

        assertThat(specRequests.get() > 0, is(true));
        assertThat(httpState.getRequestMatchers().retrieveActiveExpectations(null).size(), is(1));
    }

    @Test
    public void putExpectationIsRefusedASpecAtADeniedAddressAfterTheSameSpecLoadedWithoutTheList() {
        HttpRequest put = request("/mockserver/expectation").withMethod("PUT").withBody(openAPIExpectationJson());
        httpState(configuration().forwardProxyBlockPrivateNetworks(false)).handle(put, mock(ResponseWriter.class), false);
        int fetchedWithoutTheList = specRequests.get();
        HttpState denying = httpState(configuration().forwardProxyBlockPrivateNetworks(false).forwardProxyDeniedHosts("127.0.0.0/8"));

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> denying.handle(put, mock(ResponseWriter.class), false));

        assertThat(refused.getMessage(), containsString("denied by forwardProxyDeniedHosts"));
        assertThat(fetchedWithoutTheList > 0, is(true));
        assertThat(specRequests.get(), is(fetchedWithoutTheList));
        assertThat(denying.getRequestMatchers().retrieveActiveExpectations(null), is(empty()));
    }

    @Test
    public void putExpectationIsRefusedASpecNotOnTheAllowList() {
        HttpState allowing = httpState(configuration().forwardProxyBlockPrivateNetworks(false).forwardProxyAllowedHosts("*.example.com"));
        HttpRequest put = request("/mockserver/expectation").withMethod("PUT").withBody(openAPIExpectationJson());

        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> allowing.handle(put, mock(ResponseWriter.class), false));

        assertThat(refused.getMessage(), containsString("not in forwardProxyAllowedHosts"));
        assertThat(specRequests.get(), is(0));
    }

    @Test
    public void putExpectationFetchesASpecOnTheAllowList() {
        HttpState allowing = httpState(configuration().forwardProxyBlockPrivateNetworks(false).forwardProxyAllowedHosts("127.0.0.1"));
        HttpRequest put = request("/mockserver/expectation").withMethod("PUT").withBody(openAPIExpectationJson());

        allowing.handle(put, mock(ResponseWriter.class), false);

        assertThat(specRequests.get() > 0, is(true));
        assertThat(allowing.getRequestMatchers().retrieveActiveExpectations(null).size(), is(1));
    }

    @Test
    public void initializationJsonIsRefusedASpecAtAPrivateAddress() throws IOException {
        assertThat(loadInitializationJson(blocking()).length, is(0));
        assertThat(specRequests.get(), is(0));
    }

    @Test
    public void initializationJsonFetchesASpecAtAPrivateAddressWithTheSettingOff() throws IOException {
        assertThat(loadInitializationJson(configuration().forwardProxyBlockPrivateNetworks(false)).length, is(1));
        assertThat(specRequests.get() > 0, is(true));
    }

    private Expectation[] loadInitializationJson(Configuration configuration) throws IOException {
        configuration.initializationJsonPath(file(openAPIExpectationJson()).getAbsolutePath());
        return new ExpectationInitializerLoader(configuration, new MockServerLogger(configuration, OpenAPISpecFetchServerSettingTest.class), mock(RequestMatchers.class)).loadExpectations();
    }

    @Test
    public void persistedExpectationsRestoredFromABlobStoreAreRefusedASpecAtAPrivateAddress() throws IOException {
        assertThat(restoreFromBlobStore(blocking()), is(0));
        assertThat(specRequests.get(), is(0));
    }

    @Test
    public void persistedExpectationsRestoredFromABlobStoreFetchASpecAtAPrivateAddressWithTheSettingOff() throws IOException {
        assertThat(restoreFromBlobStore(configuration().forwardProxyBlockPrivateNetworks(false)), is(1));
        assertThat(specRequests.get() > 0, is(true));
    }

    private int restoreFromBlobStore(Configuration configuration) throws IOException {
        File persisted = file("");
        configuration.persistExpectations(true).persistedExpectationsPath(persisted.getAbsolutePath());
        RequestMatchers requestMatchers = requestMatchers(configuration);
        InMemoryBlobStore blobStore = new InMemoryBlobStore();
        blobStore.put(persisted.getName(), openAPIExpectationJson().getBytes(StandardCharsets.UTF_8), Collections.emptyMap());
        ExpectationFileSystemPersistence persistence = new ExpectationFileSystemPersistence(configuration, new MockServerLogger(configuration, OpenAPISpecFetchServerSettingTest.class), requestMatchers, blobStore);
        try {
            return requestMatchers.retrieveActiveExpectations(null).size();
        } finally {
            persistence.stop();
        }
    }
}
