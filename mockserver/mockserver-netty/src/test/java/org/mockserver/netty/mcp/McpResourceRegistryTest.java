package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.HttpState;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;

public class McpResourceRegistryTest {

    private McpResourceRegistry resourceRegistry;
    private HttpState httpState;

    @Before
    public void setUp() {
        httpState = new HttpState(configuration(), new MockServerLogger(), mock(Scheduler.class));
        resourceRegistry = new McpResourceRegistry(httpState);
    }

    @Test
    public void shouldRegisterAllResources() {
        Map<String, McpResourceRegistry.ResourceDefinition> resources = resourceRegistry.getResources();
        assertThat(resources.size(), is(4));
        assertThat(resources.containsKey("mockserver://expectations"), is(true));
        assertThat(resources.containsKey("mockserver://requests"), is(true));
        assertThat(resources.containsKey("mockserver://logs"), is(true));
        assertThat(resources.containsKey("mockserver://configuration"), is(true));
    }

    @Test
    public void shouldHaveResourceDefinitionsWithMetadata() {
        for (McpResourceRegistry.ResourceDefinition resource : resourceRegistry.getResources().values()) {
            assertThat(resource.getUri(), notNullValue());
            assertThat(resource.getName(), notNullValue());
            assertThat(resource.getDescription(), notNullValue());
            assertThat(resource.getMimeType(), notNullValue());
        }
    }

    @Test
    public void shouldReadExpectationsResource() {
        JsonNode result = resourceRegistry.readResource("mockserver://expectations");
        assertThat(result, notNullValue());
        assertThat(result.isArray(), is(true));
    }

    @Test
    public void shouldReadRequestsResource() {
        JsonNode result = resourceRegistry.readResource("mockserver://requests");
        assertThat(result, notNullValue());
        assertThat(result.isArray(), is(true));
    }

    @Test
    public void shouldReadLogsResource() {
        JsonNode result = resourceRegistry.readResource("mockserver://logs");
        assertThat(result, notNullValue());
        assertThat(result.has("logs"), is(true));
    }

    @Test
    public void shouldReadConfigurationResource() {
        JsonNode result = resourceRegistry.readResource("mockserver://configuration");
        assertThat(result, notNullValue());
        assertThat(result.has("maxExpectations"), is(true));
        assertThat(result.has("maxLogEntries"), is(true));
    }

    @Test
    public void shouldReturnNullForUnknownResource() {
        JsonNode result = resourceRegistry.readResource("mockserver://nonexistent");
        assertThat(result, nullValue());
    }

    @Test
    public void shouldHaveCorrectMimeTypes() {
        Map<String, McpResourceRegistry.ResourceDefinition> resources = resourceRegistry.getResources();
        assertThat(resources.get("mockserver://expectations").getMimeType(), is("application/json"));
        assertThat(resources.get("mockserver://requests").getMimeType(), is("application/json"));
        assertThat(resources.get("mockserver://logs").getMimeType(), is("text/plain"));
        assertThat(resources.get("mockserver://configuration").getMimeType(), is("application/json"));
    }
}
