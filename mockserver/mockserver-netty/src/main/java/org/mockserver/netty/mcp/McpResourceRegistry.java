package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.mock.HttpState;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.*;
import java.util.function.Supplier;

import static org.mockserver.model.HttpRequest.request;

public class McpResourceRegistry {

    private final HttpState httpState;
    private final ObjectMapper objectMapper;
    private final Map<String, ResourceDefinition> resources;

    public McpResourceRegistry(HttpState httpState) {
        this.httpState = httpState;
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
        this.resources = new LinkedHashMap<>();
        registerAllResources();
    }

    public Map<String, ResourceDefinition> getResources() {
        return resources;
    }

    public JsonNode readResource(String uri) {
        ResourceDefinition resource = resources.get(uri);
        if (resource == null) {
            return null;
        }
        return resource.handler.get();
    }

    private void registerAllResources() {
        resources.put("mockserver://expectations", new ResourceDefinition(
            "mockserver://expectations",
            "Active Expectations",
            "All currently active expectations configured in MockServer",
            "application/json",
            this::readExpectations
        ));

        resources.put("mockserver://requests", new ResourceDefinition(
            "mockserver://requests",
            "Recorded Requests",
            "Recent recorded requests received by MockServer (last 50)",
            "application/json",
            this::readRequests
        ));

        resources.put("mockserver://logs", new ResourceDefinition(
            "mockserver://logs",
            "Server Logs",
            "Recent MockServer log entries",
            "text/plain",
            this::readLogs
        ));

        resources.put("mockserver://configuration", new ResourceDefinition(
            "mockserver://configuration",
            "Server Configuration",
            "Current MockServer configuration properties",
            "application/json",
            this::readConfiguration
        ));
    }

    private JsonNode readExpectations() {
        try {
            HttpRequest retrieveRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", "ACTIVE_EXPECTATIONS")
                .withQueryStringParameter("format", "JSON");

            HttpResponse response = httpState.retrieve(retrieveRequest);
            String body = response.getBodyAsString();
            if (body != null && !body.isEmpty()) {
                return objectMapper.readTree(body);
            }
            return objectMapper.createArrayNode();
        } catch (Exception e) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to read expectations");
            return error;
        }
    }

    private JsonNode readRequests() {
        try {
            HttpRequest retrieveRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", "REQUESTS")
                .withQueryStringParameter("format", "JSON");

            HttpResponse response = httpState.retrieve(retrieveRequest);
            String body = response.getBodyAsString();
            if (body != null && !body.isEmpty()) {
                JsonNode allRequests = objectMapper.readTree(body);
                if (allRequests.isArray() && allRequests.size() > 50) {
                    ArrayNode limited = objectMapper.createArrayNode();
                    for (int i = allRequests.size() - 50; i < allRequests.size(); i++) {
                        limited.add(allRequests.get(i));
                    }
                    return limited;
                }
                return allRequests;
            }
            return objectMapper.createArrayNode();
        } catch (Exception e) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to read requests");
            return error;
        }
    }

    private JsonNode readLogs() {
        try {
            HttpRequest retrieveRequest = request()
                .withMethod("PUT")
                .withPath("/mockserver/retrieve")
                .withQueryStringParameter("type", "LOGS")
                .withQueryStringParameter("format", "JSON");

            HttpResponse response = httpState.retrieve(retrieveRequest);
            String body = response.getBodyAsString();
            ObjectNode result = objectMapper.createObjectNode();
            result.put("logs", body != null ? body : "");
            return result;
        } catch (Exception e) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to read logs");
            return error;
        }
    }

    private JsonNode readConfiguration() {
        try {
            ObjectNode config = objectMapper.createObjectNode();
            config.put("maxExpectations", ConfigurationProperties.maxExpectations());
            config.put("maxLogEntries", ConfigurationProperties.maxLogEntries());
            return config;
        } catch (Exception e) {
            ObjectNode error = objectMapper.createObjectNode();
            error.put("error", "Failed to read configuration");
            return error;
        }
    }

    public static class ResourceDefinition {
        private final String uri;
        private final String name;
        private final String description;
        private final String mimeType;
        private final Supplier<JsonNode> handler;

        public ResourceDefinition(String uri, String name, String description, String mimeType, Supplier<JsonNode> handler) {
            this.uri = uri;
            this.name = name;
            this.description = description;
            this.mimeType = mimeType;
            this.handler = handler;
        }

        public String getUri() {
            return uri;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public String getMimeType() {
            return mimeType;
        }
    }
}
