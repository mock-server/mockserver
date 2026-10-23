package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.lifecycle.LifeCycle;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.ConfigurationDTO;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.arrayWithSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * A {@code PUT /mockserver/configuration} that repoints the default LLM backend must not carry the held
 * {@code llmApiKey} to the new host. Drives the live path that reads the key and base URL together on each
 * call (the {@code detect_llm_drift} tool's {@code LlmBackendResolver}) against a stub at the new host.
 */
public class LlmApiKeyDestinationChangeTest {

    private static final String HELD_KEY = "sk-held-secret";
    private static final String COMPLETION = "{\"id\":\"c1\",\"object\":\"chat.completion\",\"model\":\"gpt-4o\","
        + "\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"hi\"},\"finish_reason\":\"stop\"}]}";

    private ClientAndServer newHost;
    private HttpState httpState;
    private Configuration configuration;
    private McpToolRegistry toolRegistry;
    private Path cassette;

    @Before
    public void setUp() throws Exception {
        newHost = ClientAndServer.startClientAndServer(0);
        newHost.when(request().withMethod("POST").withPath("/v1/chat/completions"))
            .respond(response().withStatusCode(200).withHeader("Content-Type", "application/json").withBody(COMPLETION));
        configuration = configuration().llmProvider("OPENAI").llmBaseUrl("http://127.0.0.1:1").llmApiKey(HELD_KEY);
        httpState = new HttpState(configuration, new MockServerLogger(), mock(Scheduler.class));
        LifeCycle server = mock(LifeCycle.class);
        when(server.getScheduler()).thenReturn(mock(Scheduler.class));
        toolRegistry = new McpToolRegistry(httpState, server);
        cassette = Files.createTempFile("llm-cassette", ".json");
        Files.write(cassette, ("[{\"httpRequest\":{\"method\":\"POST\",\"path\":\"/v1/chat/completions\","
            + "\"body\":{\"type\":\"JSON\",\"json\":{\"model\":\"gpt-4o\",\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}]}}},"
            + "\"httpResponse\":{\"statusCode\":200,\"body\":{\"type\":\"JSON\",\"json\":" + COMPLETION + "}}}]").getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() throws Exception {
        httpState.stop();
        newHost.stop();
        Files.deleteIfExists(cassette);
    }

    private void put(ConfigurationDTO update) {
        update.applyTo(configuration);
        httpState.applyConfigurationUpdate(update);
    }

    private RequestDefinition[] callsToNewHost() {
        ObjectNode params = ObjectMapperFactory.createObjectMapper().createObjectNode();
        params.put("provider", "OPENAI");
        params.put("cassettePath", cassette.toString());
        toolRegistry.callTool("detect_llm_drift", params);
        return newHost.retrieveRecordedRequests(request().withPath("/v1/chat/completions"));
    }

    @Test
    public void shouldNotSendTheHeldKeyToANewBaseUrlSuppliedWithoutAKey() {
        put(new ConfigurationDTO().setLlmBaseUrl("http://127.0.0.1:" + newHost.getPort()));

        RequestDefinition[] calls = callsToNewHost();

        assertThat("the call must reach the new host, or the assertion below proves nothing", calls, arrayWithSize(1));
        HttpRequest call = (HttpRequest) calls[0];
        assertThat(call.containsHeader("Authorization"), is(false));
        assertThat(call.toString().contains(HELD_KEY), is(false));
    }

    @Test
    public void shouldSendTheKeySuppliedWithTheNewBaseUrl() {
        put(new ConfigurationDTO().setLlmBaseUrl("http://127.0.0.1:" + newHost.getPort()).setLlmApiKey("sk-for-the-new-host"));

        RequestDefinition[] calls = callsToNewHost();

        assertThat(calls, arrayWithSize(1));
        HttpRequest call = (HttpRequest) calls[0];
        assertThat(call.getFirstHeader("Authorization"), is("Bearer sk-for-the-new-host"));
        assertThat(call.getFirstHeader("Authorization"), not(is("Bearer " + HELD_KEY)));
    }
}
