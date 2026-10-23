package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.http.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.authentication.AuthenticationHandler;
import org.mockserver.authentication.AuthenticationResult;
import org.mockserver.authentication.authorization.ControlPlaneRole;
import org.mockserver.configuration.Configuration;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;

/**
 * An MCP tool call is authenticated when its request arrives and authorized later, when the tool is
 * dispatched. A {@code PUT /mockserver/configuration} applied between the two must not pair the old
 * authentication with the new authorization settings: the call is authorized from the settings it was
 * authenticated under.
 */
public class McpToolAuthorizationSnapshotTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.buildObjectMapperWithoutRemovingEmptyValues();
    private LifeCycle server;
    private Configuration configuration;
    private HttpState httpState;
    private EmbeddedChannel channel;
    private final AtomicBoolean putDuringNextAuthentication = new AtomicBoolean();

    @Before
    public void setUp() {
        server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        when(server.getLocalPorts()).thenReturn(List.of(1080));
        when(server.isRunning()).thenReturn(true);

        // READ only for the principal's scope, so a mutating tool is forbidden while authorization is enabled
        Map<String, ControlPlaneRole> mapping = Collections.singletonMap("readers", ControlPlaneRole.READ);
        configuration = configuration().controlPlaneAuthorizationEnabled(true).controlPlaneScopeMapping(mapping);
        httpState = new HttpState(configuration, new MockServerLogger(), mock(Scheduler.class));
        httpState.setControlPlaneAuthenticationHandler(new AuthenticationHandler() {
            @Override
            public boolean controlPlaneRequestAuthenticated(HttpRequest request) {
                return true;
            }

            @Override
            public AuthenticationResult authenticate(HttpRequest request) {
                if (putDuringNextAuthentication.getAndSet(false)) {
                    // the change lands after the request has been authenticated and before its tool is authorized; a PUT
                    // cannot make it (the setting is fixed at startup), so it is made in process
                    configuration.controlPlaneAuthorizationEnabled(false);
                }
                return AuthenticationResult.authenticated("principal", "verified-oidc", Collections.emptyMap(), Set.of("readers"));
            }
        });
        channel = new EmbeddedChannel(new McpStreamableHttpHandler(httpState, server, new McpSessionManager(httpState.getMockServerLogger())));
    }

    @After
    public void tearDown() {
        channel.close();
        httpState.stop();
    }

    @Test
    public void shouldAuthorizeToolCallFromTheSettingsItWasAuthenticatedUnder() throws Exception {
        String sessionId = initialize();

        putDuringNextAuthentication.set(true);
        JsonNode response = callCreateExpectation(sessionId, "/during-put");

        assertThat("the PUT ran between authentication and authorization", configuration.controlPlaneAuthorizationEnabled(), is(false));
        assertThat("the tool is authorized from the settings it was authenticated under, which forbid it",
            response.path("error").path("message").asText(), containsString("Forbidden for control plane"));
        assertThat(httpState.firstMatchingExpectation(request().withMethod("GET").withPath("/during-put")), is(nullValue()));
    }

    @Test
    public void shouldAuthorizeTheNextToolCallFromTheNewSettings() throws Exception {
        String sessionId = initialize();
        putDuringNextAuthentication.set(true);
        callCreateExpectation(sessionId, "/during-put");

        JsonNode response = callCreateExpectation(sessionId, "/after-put");

        assertThat("authorization is disabled for a request authenticated after the PUT",
            response.path("error").isMissingNode(), is(true));
        assertThat(httpState.firstMatchingExpectation(request().withMethod("GET").withPath("/after-put")), is(notNullValue()));
    }

    private String initialize() {
        FullHttpResponse init = post("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{}}", null);
        String sessionId = init.headers().get("Mcp-Session-Id");
        init.release();
        FullHttpResponse initialized = post("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", sessionId);
        if (initialized != null) {
            initialized.release();
        }
        return sessionId;
    }

    private JsonNode callCreateExpectation(String sessionId, String path) throws Exception {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":7,\"method\":\"tools/call\",\"params\":{\"name\":\"create_expectation\","
            + "\"arguments\":{\"method\":\"GET\",\"path\":\"" + path + "\",\"statusCode\":201}}}";
        FullHttpResponse response = post(body, sessionId);
        assertThat(response, notNullValue());
        try {
            return objectMapper.readTree(response.content().toString(StandardCharsets.UTF_8));
        } finally {
            response.release();
        }
    }

    private FullHttpResponse post(String body, String sessionId) {
        FullHttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.POST, "/mockserver/mcp",
            Unpooled.copiedBuffer(body, StandardCharsets.UTF_8));
        request.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
        if (sessionId != null) {
            request.headers().set("Mcp-Session-Id", sessionId);
        }
        channel.writeInbound(request);
        // tool calls are answered from the MCP executor, not the calling thread
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            FullHttpResponse response = channel.readOutbound();
            if (response != null) {
                return response;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return null;
    }
}
