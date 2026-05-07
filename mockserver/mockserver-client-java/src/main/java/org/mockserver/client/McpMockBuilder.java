package org.mockserver.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.util.*;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.JsonRpcBody.jsonRpc;

public class McpMockBuilder {

    private String path = "/mcp";
    private String serverName = "MockMCPServer";
    private String serverVersion = "1.0.0";
    private String protocolVersion = "2025-03-26";
    private boolean toolsCapability = false;
    private boolean resourcesCapability = false;
    private boolean promptsCapability = false;
    private final List<McpToolDefinition> tools = new ArrayList<>();
    private final List<McpResourceDefinition> resources = new ArrayList<>();
    private final List<McpPromptDefinition> prompts = new ArrayList<>();

    private McpMockBuilder() {
    }

    public static McpMockBuilder mcpMock() {
        return new McpMockBuilder();
    }

    public static McpMockBuilder mcpMock(String path) {
        McpMockBuilder builder = new McpMockBuilder();
        builder.path = path;
        return builder;
    }

    public McpMockBuilder withServerName(String name) {
        this.serverName = name;
        return this;
    }

    public McpMockBuilder withServerVersion(String version) {
        this.serverVersion = version;
        return this;
    }

    public McpMockBuilder withProtocolVersion(String version) {
        this.protocolVersion = version;
        return this;
    }

    public McpMockBuilder withToolsCapability() {
        this.toolsCapability = true;
        return this;
    }

    public McpMockBuilder withResourcesCapability() {
        this.resourcesCapability = true;
        return this;
    }

    public McpMockBuilder withPromptsCapability() {
        this.promptsCapability = true;
        return this;
    }

    public McpToolBuilder withTool(String name) {
        return new McpToolBuilder(this, name);
    }

    public McpResourceBuilder withResource(String uri) {
        return new McpResourceBuilder(this, uri);
    }

    public McpPromptBuilder withPrompt(String name) {
        return new McpPromptBuilder(this, name);
    }

    public Expectation[] applyTo(MockServerClient client) {
        return client.upsert(build());
    }

    public Expectation[] build() {
        List<Expectation> expectations = new ArrayList<>();

        expectations.add(buildInitializeExpectation());
        expectations.add(buildPingExpectation());
        expectations.add(buildNotificationsInitializedExpectation());

        if (toolsCapability || !tools.isEmpty()) {
            expectations.add(buildToolsListExpectation());
        }
        for (McpToolDefinition tool : tools) {
            expectations.add(buildToolsCallExpectation(tool));
        }

        if (resourcesCapability || !resources.isEmpty()) {
            expectations.add(buildResourcesListExpectation());
        }
        for (McpResourceDefinition resource : resources) {
            expectations.add(buildResourcesReadExpectation(resource));
        }

        if (promptsCapability || !prompts.isEmpty()) {
            expectations.add(buildPromptsListExpectation());
        }
        for (McpPromptDefinition prompt : prompts) {
            expectations.add(buildPromptsGetExpectation(prompt));
        }

        return expectations.toArray(new Expectation[0]);
    }

    private Expectation buildInitializeExpectation() {
        StringBuilder caps = new StringBuilder("{");
        boolean first = true;
        if (toolsCapability || !tools.isEmpty()) {
            caps.append("\"tools\": {\"listChanged\": false}");
            first = false;
        }
        if (resourcesCapability || !resources.isEmpty()) {
            if (!first) {
                caps.append(", ");
            }
            caps.append("\"resources\": {\"subscribe\": false, \"listChanged\": false}");
            first = false;
        }
        if (promptsCapability || !prompts.isEmpty()) {
            if (!first) {
                caps.append(", ");
            }
            caps.append("\"prompts\": {\"listChanged\": false}");
        }
        caps.append("}");

        String resultJson = "{\"protocolVersion\": \"" + escapeVelocity(escapeJson(protocolVersion)) + "\", " +
            "\"capabilities\": " + caps + ", " +
            "\"serverInfo\": {\"name\": \"" + escapeVelocity(escapeJson(serverName)) + "\", \"version\": \"" + escapeVelocity(escapeJson(serverVersion)) + "\"}}";

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("initialize"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildPingExpectation() {
        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("ping"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse("{}")));
    }

    private Expectation buildNotificationsInitializedExpectation() {
        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("notifications/initialized"))
        ).thenRespond(
            HttpResponse.response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")
        );
    }

    private Expectation buildToolsListExpectation() {
        StringBuilder toolsJson = new StringBuilder("[");
        for (int i = 0; i < tools.size(); i++) {
            if (i > 0) {
                toolsJson.append(", ");
            }
            McpToolDefinition tool = tools.get(i);
            toolsJson.append("{\"name\": \"").append(escapeVelocity(escapeJson(tool.name))).append("\"");
            if (tool.description != null) {
                toolsJson.append(", \"description\": \"").append(escapeVelocity(escapeJson(tool.description))).append("\"");
            }
            if (tool.inputSchema != null) {
                toolsJson.append(", \"inputSchema\": ").append(escapeVelocity(validateAndSerializeJson(tool.inputSchema)));
            }
            toolsJson.append("}");
        }
        toolsJson.append("]");

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("tools/list"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse("{\"tools\": " + toolsJson + "}")));
    }

    private Expectation buildToolsCallExpectation(McpToolDefinition tool) {
        String jsonPathBody = "$[?(@.method == 'tools/call' && @.params.name == '" + escapeJsonPath(tool.name) + "')]";
        String content = tool.responseContent != null ? escapeVelocity(escapeJson(tool.responseContent)) : "";
        String isError = tool.responseIsError ? "true" : "false";
        String resultJson = "{\"content\": [{\"type\": \"text\", \"text\": \"" + content + "\"}], \"isError\": " + isError + "}";

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(new JsonPathBody(jsonPathBody))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildResourcesListExpectation() {
        StringBuilder resourcesJson = new StringBuilder("[");
        for (int i = 0; i < resources.size(); i++) {
            if (i > 0) {
                resourcesJson.append(", ");
            }
            McpResourceDefinition resource = resources.get(i);
            resourcesJson.append("{\"uri\": \"").append(escapeVelocity(escapeJson(resource.uri))).append("\"");
            if (resource.name != null) {
                resourcesJson.append(", \"name\": \"").append(escapeVelocity(escapeJson(resource.name))).append("\"");
            }
            if (resource.description != null) {
                resourcesJson.append(", \"description\": \"").append(escapeVelocity(escapeJson(resource.description))).append("\"");
            }
            if (resource.mimeType != null) {
                resourcesJson.append(", \"mimeType\": \"").append(escapeVelocity(escapeJson(resource.mimeType))).append("\"");
            }
            resourcesJson.append("}");
        }
        resourcesJson.append("]");

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("resources/list"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse("{\"resources\": " + resourcesJson + "}")));
    }

    private Expectation buildResourcesReadExpectation(McpResourceDefinition resource) {
        String jsonPathBody = "$[?(@.method == 'resources/read' && @.params.uri == '" + escapeJsonPath(resource.uri) + "')]";
        String content = resource.content != null ? escapeVelocity(escapeJson(resource.content)) : "";
        String mimeType = resource.mimeType != null ? resource.mimeType : "application/json";
        String resultJson = "{\"contents\": [{\"uri\": \"" + escapeVelocity(escapeJson(resource.uri)) + "\", " +
            "\"mimeType\": \"" + escapeVelocity(escapeJson(mimeType)) + "\", " +
            "\"text\": \"" + content + "\"}]}";

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(new JsonPathBody(jsonPathBody))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildPromptsListExpectation() {
        StringBuilder promptsJson = new StringBuilder("[");
        for (int i = 0; i < prompts.size(); i++) {
            if (i > 0) {
                promptsJson.append(", ");
            }
            McpPromptDefinition prompt = prompts.get(i);
            promptsJson.append("{\"name\": \"").append(escapeVelocity(escapeJson(prompt.name))).append("\"");
            if (prompt.description != null) {
                promptsJson.append(", \"description\": \"").append(escapeVelocity(escapeJson(prompt.description))).append("\"");
            }
            if (!prompt.arguments.isEmpty()) {
                promptsJson.append(", \"arguments\": [");
                for (int j = 0; j < prompt.arguments.size(); j++) {
                    if (j > 0) {
                        promptsJson.append(", ");
                    }
                    McpPromptArgument arg = prompt.arguments.get(j);
                    promptsJson.append("{\"name\": \"").append(escapeVelocity(escapeJson(arg.name))).append("\"");
                    if (arg.description != null) {
                        promptsJson.append(", \"description\": \"").append(escapeVelocity(escapeJson(arg.description))).append("\"");
                    }
                    promptsJson.append(", \"required\": ").append(arg.required);
                    promptsJson.append("}");
                }
                promptsJson.append("]");
            }
            promptsJson.append("}");
        }
        promptsJson.append("]");

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("prompts/list"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse("{\"prompts\": " + promptsJson + "}")));
    }

    private Expectation buildPromptsGetExpectation(McpPromptDefinition prompt) {
        String jsonPathBody = "$[?(@.method == 'prompts/get' && @.params.name == '" + escapeJsonPath(prompt.name) + "')]";
        StringBuilder messagesJson = new StringBuilder("[");
        for (int i = 0; i < prompt.messages.size(); i++) {
            if (i > 0) {
                messagesJson.append(", ");
            }
            McpPromptMessage msg = prompt.messages.get(i);
            messagesJson.append("{\"role\": \"").append(escapeVelocity(escapeJson(msg.role))).append("\", ");
            messagesJson.append("\"content\": {\"type\": \"text\", \"text\": \"").append(escapeVelocity(escapeJson(msg.text))).append("\"}}");
        }
        messagesJson.append("]");

        String resultJson = "{\"messages\": " + messagesJson + "}";

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(new JsonPathBody(jsonPathBody))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private String velocityJsonRpcResponse(String resultJson) {
        return "{\"statusCode\": 200, " +
            "\"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], " +
            "\"body\": {\"jsonrpc\": \"2.0\", \"result\": " + resultJson + ", \"id\": $!{request.jsonRpcRawId}}}";
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        try {
            String quoted = OBJECT_MAPPER.writeValueAsString(value);
            return quoted.substring(1, quoted.length() - 1);
        } catch (Exception e) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
        }
    }

    private static String escapeVelocity(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("$", "${esc.d}").replace("#", "${esc.h}");
    }

    private static String validateAndSerializeJson(String json) {
        try {
            JsonNode parsedTree = OBJECT_MAPPER.readTree(json);
            return OBJECT_MAPPER.writeValueAsString(parsedTree);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON for inputSchema: " + e.getMessage(), e);
        }
    }

    private static String escapeJsonPath(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("'", "\\'");
    }

    static class McpToolDefinition {
        final String name;
        String description;
        String inputSchema;
        String responseContent;
        boolean responseIsError = false;

        McpToolDefinition(String name) {
            this.name = name;
        }
    }

    static class McpResourceDefinition {
        final String uri;
        String name;
        String description;
        String mimeType = "application/json";
        String content;

        McpResourceDefinition(String uri) {
            this.uri = uri;
        }
    }

    static class McpPromptDefinition {
        final String name;
        String description;
        final List<McpPromptArgument> arguments = new ArrayList<>();
        final List<McpPromptMessage> messages = new ArrayList<>();

        McpPromptDefinition(String name) {
            this.name = name;
        }
    }

    static class McpPromptArgument {
        final String name;
        String description;
        boolean required;

        McpPromptArgument(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }
    }

    static class McpPromptMessage {
        final String role;
        final String text;

        McpPromptMessage(String role, String text) {
            this.role = role;
            this.text = text;
        }
    }

    public static class McpToolBuilder {
        private final McpMockBuilder parent;
        private final McpToolDefinition tool;

        McpToolBuilder(McpMockBuilder parent, String name) {
            this.parent = parent;
            this.tool = new McpToolDefinition(name);
        }

        public McpToolBuilder withDescription(String description) {
            tool.description = description;
            return this;
        }

        public McpToolBuilder withInputSchema(String jsonSchema) {
            tool.inputSchema = jsonSchema;
            return this;
        }

        public McpToolBuilder respondingWith(String textContent) {
            tool.responseContent = textContent;
            return this;
        }

        public McpToolBuilder respondingWith(String textContent, boolean isError) {
            tool.responseContent = textContent;
            tool.responseIsError = isError;
            return this;
        }

        public McpMockBuilder and() {
            parent.tools.add(tool);
            if (!parent.toolsCapability) {
                parent.toolsCapability = true;
            }
            return parent;
        }
    }

    public static class McpResourceBuilder {
        private final McpMockBuilder parent;
        private final McpResourceDefinition resource;

        McpResourceBuilder(McpMockBuilder parent, String uri) {
            this.parent = parent;
            this.resource = new McpResourceDefinition(uri);
        }

        public McpResourceBuilder withName(String name) {
            resource.name = name;
            return this;
        }

        public McpResourceBuilder withDescription(String description) {
            resource.description = description;
            return this;
        }

        public McpResourceBuilder withMimeType(String mimeType) {
            resource.mimeType = mimeType;
            return this;
        }

        public McpResourceBuilder withContent(String content) {
            resource.content = content;
            return this;
        }

        public McpMockBuilder and() {
            parent.resources.add(resource);
            if (!parent.resourcesCapability) {
                parent.resourcesCapability = true;
            }
            return parent;
        }
    }

    public static class McpPromptBuilder {
        private final McpMockBuilder parent;
        private final McpPromptDefinition prompt;

        McpPromptBuilder(McpMockBuilder parent, String name) {
            this.parent = parent;
            this.prompt = new McpPromptDefinition(name);
        }

        public McpPromptBuilder withDescription(String description) {
            prompt.description = description;
            return this;
        }

        public McpPromptBuilder withArgument(String name, String description, boolean required) {
            prompt.arguments.add(new McpPromptArgument(name, description, required));
            return this;
        }

        public McpPromptBuilder respondingWith(String role, String textContent) {
            prompt.messages.add(new McpPromptMessage(role, textContent));
            return this;
        }

        public McpMockBuilder and() {
            parent.prompts.add(prompt);
            if (!parent.promptsCapability) {
                parent.promptsCapability = true;
            }
            return parent;
        }
    }
}
