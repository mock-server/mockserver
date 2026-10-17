package org.mockserver.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import java.net.URI;
import java.util.*;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;
import static org.mockserver.model.HttpTemplate.TemplateType.VELOCITY;
import static org.mockserver.model.JsonRpcBody.jsonRpc;

public class A2aMockBuilder {

    private String path = "/a2a";
    private String agentCardPath = "/.well-known/agent.json";
    private String agentName = "MockAgent";
    private String agentDescription = "A mock A2A agent";
    private String agentVersion = "1.0.0";
    private String agentUrl;
    private final List<A2aSkillDefinition> skills = new ArrayList<>();
    private final List<A2aTaskHandler> taskHandlers = new ArrayList<>();
    private String defaultTaskResponse = "Task completed successfully";
    private boolean streaming = false;
    private String streamingMethod = "message/stream";
    private String pushNotificationUrl;

    private A2aMockBuilder() {
    }

    public static A2aMockBuilder a2aMock() {
        return new A2aMockBuilder();
    }

    public static A2aMockBuilder a2aMock(String path) {
        A2aMockBuilder builder = new A2aMockBuilder();
        builder.path = path;
        return builder;
    }

    public A2aMockBuilder withAgentName(String name) {
        this.agentName = name;
        return this;
    }

    public A2aMockBuilder withAgentDescription(String description) {
        this.agentDescription = description;
        return this;
    }

    public A2aMockBuilder withAgentVersion(String version) {
        this.agentVersion = version;
        return this;
    }

    public A2aMockBuilder withAgentUrl(String url) {
        this.agentUrl = url;
        return this;
    }

    public A2aMockBuilder withAgentCardPath(String path) {
        this.agentCardPath = path;
        return this;
    }

    public A2aMockBuilder withDefaultTaskResponse(String response) {
        this.defaultTaskResponse = response;
        return this;
    }

    /**
     * Advertise and mock the A2A streaming capability. When enabled the agent card reports
     * {@code capabilities.streaming: true} and a streaming JSON-RPC method (default
     * {@code message/stream}, see {@link #withStreamingMethod(String)}) returns an
     * SSE stream of {@code TaskStatusUpdateEvent} and {@code TaskArtifactUpdateEvent}
     * chunks, each wrapped in a JSON-RPC 2.0 response envelope.
     */
    public A2aMockBuilder withStreaming() {
        this.streaming = true;
        return this;
    }

    /**
     * Override the JSON-RPC method that triggers the streaming response. The A2A specification
     * uses {@code message/stream}; the legacy method name is {@code tasks/sendSubscribe}.
     * Implies {@link #withStreaming()}.
     */
    public A2aMockBuilder withStreamingMethod(String method) {
        this.streamingMethod = method;
        this.streaming = true;
        return this;
    }

    /**
     * Advertise and mock A2A push notifications. When configured the agent card reports
     * {@code capabilities.pushNotifications: true}, the {@code tasks/pushNotificationConfig/set}
     * method echoes the registered config, and each {@code tasks/send} additionally POSTs the
     * completed task (the A2A push-notification payload) to the supplied webhook URL while still
     * returning the JSON-RPC task response to the caller.
     *
     * @param webhookUrl absolute webhook URL (e.g. {@code http://localhost:1234/a2a/callback})
     */
    public A2aMockBuilder withPushNotifications(String webhookUrl) {
        this.pushNotificationUrl = webhookUrl;
        return this;
    }

    public A2aSkillBuilder withSkill(String id) {
        return new A2aSkillBuilder(this, id);
    }

    public A2aTaskHandlerBuilder onTaskSend() {
        return new A2aTaskHandlerBuilder(this);
    }

    public Expectation[] applyTo(MockServerClient client) {
        return client.upsert(build());
    }

    public Expectation[] build() {
        List<Expectation> expectations = new ArrayList<>();

        expectations.add(buildAgentCardExpectation());

        for (A2aTaskHandler handler : taskHandlers) {
            expectations.add(buildCustomTaskHandler(handler));
        }

        if (streaming) {
            expectations.add(buildStreamingExpectation());
        }

        if (pushNotificationUrl != null) {
            expectations.add(buildPushNotificationConfigExpectation());
            expectations.add(buildPushNotificationDeliveryExpectation());
        } else {
            expectations.add(buildTasksSendExpectation());
        }
        expectations.add(buildTasksGetExpectation());
        expectations.add(buildTasksCancelExpectation());

        return expectations.toArray(new Expectation[0]);
    }

    private Expectation buildAgentCardExpectation() {
        StringBuilder skillsJson = new StringBuilder("[");
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) {
                skillsJson.append(", ");
            }
            A2aSkillDefinition skill = skills.get(i);
            skillsJson.append("{");
            skillsJson.append("\"id\": \"").append(escapeJson(skill.id)).append("\"");
            skillsJson.append(", \"name\": \"").append(escapeJson(skill.name != null ? skill.name : skill.id)).append("\"");
            if (skill.description != null) {
                skillsJson.append(", \"description\": \"").append(escapeJson(skill.description)).append("\"");
            }
            if (!skill.tags.isEmpty()) {
                skillsJson.append(", \"tags\": [");
                for (int j = 0; j < skill.tags.size(); j++) {
                    if (j > 0) {
                        skillsJson.append(", ");
                    }
                    skillsJson.append("\"").append(escapeJson(skill.tags.get(j))).append("\"");
                }
                skillsJson.append("]");
            }
            if (!skill.examples.isEmpty()) {
                skillsJson.append(", \"examples\": [");
                for (int j = 0; j < skill.examples.size(); j++) {
                    if (j > 0) {
                        skillsJson.append(", ");
                    }
                    skillsJson.append("\"").append(escapeJson(skill.examples.get(j))).append("\"");
                }
                skillsJson.append("]");
            }
            skillsJson.append("}");
        }
        skillsJson.append("]");

        String url = agentUrl != null ? agentUrl : "http://localhost" + path;

        String agentCardJson = "{" +
            "\"name\": \"" + escapeJson(agentName) + "\", " +
            "\"description\": \"" + escapeJson(agentDescription) + "\", " +
            "\"version\": \"" + escapeJson(agentVersion) + "\", " +
            "\"url\": \"" + escapeJson(url) + "\", " +
            "\"capabilities\": {\"streaming\": " + streaming + ", \"pushNotifications\": " + (pushNotificationUrl != null) + ", \"stateTransitionHistory\": false}, " +
            "\"skills\": " + skillsJson + "}";

        return Expectation.when(
            request().withMethod("GET").withPath(agentCardPath)
        ).thenRespond(
            response().withStatusCode(200)
                .withHeader("Content-Type", "application/json")
                .withBody(agentCardJson)
        );
    }

    private Expectation buildTasksSendExpectation() {
        String resultJson = buildTaskResultJson(defaultTaskResponse, false);
        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("tasks/send"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildTasksGetExpectation() {
        String resultJson = buildTaskResultJson(defaultTaskResponse, false);
        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("tasks/get"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildTasksCancelExpectation() {
        String resultJson = "{\"id\": \"mock-task-id\", \"status\": {\"state\": \"canceled\"}}";
        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("tasks/cancel"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildStreamingExpectation() {
        String text = escapeJson(defaultTaskResponse);
        String taskId = "mock-task-id";

        // A2A streaming: each SSE event data is a JSON-RPC 2.0 response envelope wrapping a
        // TaskStatusUpdateEvent or TaskArtifactUpdateEvent. The JSON-RPC id is not known at
        // build time, so a stable placeholder is used (streaming clients correlate by stream).
        SseEvent statusWorking = SseEvent.sseEvent()
            .withEvent("message")
            .withData("{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                "{\"taskId\": \"" + taskId + "\", \"kind\": \"status-update\", " +
                "\"status\": {\"state\": \"working\"}, \"final\": false}}");

        SseEvent artifactUpdate = SseEvent.sseEvent()
            .withEvent("message")
            .withData("{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                "{\"taskId\": \"" + taskId + "\", \"kind\": \"artifact-update\", " +
                "\"artifact\": {\"parts\": [{\"type\": \"text\", \"text\": \"" + text + "\"}]}}}");

        SseEvent statusCompleted = SseEvent.sseEvent()
            .withEvent("message")
            .withData("{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                "{\"taskId\": \"" + taskId + "\", \"kind\": \"status-update\", " +
                "\"status\": {\"state\": \"completed\"}, \"final\": true}}");

        HttpSseResponse sseResponse = HttpSseResponse.sseResponse()
            .withStatusCode(200)
            .withEvents(statusWorking, artifactUpdate, statusCompleted)
            .withCloseConnection(true);

        Expectation expectation = Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc(streamingMethod))
        );
        expectation.thenRespondWithSse(sseResponse);
        return expectation;
    }

    private Expectation buildPushNotificationConfigExpectation() {
        // Echo the registered push-notification config back as the JSON-RPC result.
        String resultJson = "{\"url\": \"" + escapeVelocity(escapeJson(pushNotificationUrl)) + "\"}";
        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("tasks/pushNotificationConfig/set"))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private Expectation buildPushNotificationDeliveryExpectation() {
        // When push notifications are configured, a tasks/send both returns the JSON-RPC task
        // response to the caller AND POSTs the completed task (the push-notification payload) to
        // the configured webhook URL. This is modelled with an override-forwarded-request: the
        // request override targets the webhook (literal body), and a Velocity response *template*
        // produces the caller's JSON-RPC response so the request's id is echoed back.
        WebhookTarget target = WebhookTarget.parse(pushNotificationUrl);

        // Literal webhook POST body — no Velocity engine runs over a request override, so only
        // JSON escaping is applied. The push payload carries no JSON-RPC id (server-initiated).
        String pushBody = "{\"jsonrpc\": \"2.0\", \"result\": " + buildTaskResultJsonRaw(defaultTaskResponse, false) + "}";

        HttpRequest webhookRequest = request()
            .withMethod("POST")
            .withPath(target.path)
            .withSocketAddress(target.host, target.port,
                target.secure ? SocketAddress.Scheme.HTTPS : SocketAddress.Scheme.HTTP)
            .withSecure(target.secure)
            .withHeader("Host", target.hostHeader())
            .withHeader("Content-Type", "application/json")
            .withBody(pushBody);

        // Caller response — a Velocity template so $!{request.jsonRpcRawId} echoes the request id,
        // matching the non-push tasks/send contract.
        HttpTemplate clientResponseTemplate = template(VELOCITY,
            velocityJsonRpcResponse(buildTaskResultJson(defaultTaskResponse, false)));

        Expectation expectation = Expectation.when(
            request().withMethod("POST").withPath(path).withBody(jsonRpc("tasks/send"))
        );
        expectation.thenForward(
            HttpOverrideForwardedRequest.forwardOverriddenRequest(webhookRequest)
                .withResponseTemplate(clientResponseTemplate)
        );
        return expectation;
    }

    private Expectation buildCustomTaskHandler(A2aTaskHandler handler) {
        String escapedPattern = escapeMessagePattern(handler.messagePattern);
        String jsonPathBody = "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /" + escapedPattern + "/)]";
        String resultJson = buildTaskResultJson(handler.responseText, handler.isError);

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(new JsonPathBody(jsonPathBody))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    /**
     * Escapes a user-supplied regular-expression {@code messagePattern} for safe embedding between
     * the {@code /.../} delimiters of a JsonPath {@code =~} regex literal. A single-pass scan
     * preserves existing regex escape sequences (e.g. {@code \d}, {@code \/}, {@code \\}) so that
     * normal patterns are emitted byte-for-byte unchanged, while neutralizing the breakout cases:
     * a lone trailing backslash (which would otherwise escape the closing delimiter) is doubled,
     * any bare {@code /} is escaped to {@code \/}, newline/CR are turned into their two-character
     * escapes, and NUL is stripped.
     */
    private static String escapeMessagePattern(String pattern) {
        StringBuilder out = new StringBuilder(pattern.length());
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\\') {
                if (i + 1 < pattern.length()) {
                    // preserve the escape sequence verbatim (e.g. \d, \/, \\)
                    out.append('\\').append(pattern.charAt(++i));
                } else {
                    // lone trailing backslash — double it so it cannot escape the closing delimiter
                    out.append("\\\\");
                }
            } else if (c == '/') {
                out.append("\\/");
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c == '\0') {
                // strip NUL
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private String buildTaskResultJson(String responseText, boolean isError) {
        // For Velocity-templated response bodies: the text must survive the Velocity engine, so
        // metacharacters are escaped here and un-escaped by the template engine at response time.
        return taskResultJson(escapeVelocity(escapeJson(responseText)), isError);
    }

    private String buildTaskResultJsonRaw(String responseText, boolean isError) {
        // For literal (non-templated) response bodies (e.g. the webhook POST payload), where no
        // Velocity engine runs, only JSON escaping is applied — Velocity escaping would corrupt
        // any '$' / '#' into "${esc.d}" / "${esc.h}".
        return taskResultJson(escapeJson(responseText), isError);
    }

    private String taskResultJson(String escapedText, boolean isError) {
        String state = isError ? "failed" : "completed";
        return "{\"id\": \"mock-task-id\", " +
            "\"status\": {\"state\": \"" + state + "\"}, " +
            "\"artifacts\": [{\"parts\": [{\"type\": \"text\", \"text\": \"" + escapedText + "\"}]}]}";
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
        return McpMockBuilder.escapeVelocity(value);
    }

    static class A2aSkillDefinition {
        final String id;
        String name;
        String description;
        final List<String> tags = new ArrayList<>();
        final List<String> examples = new ArrayList<>();

        A2aSkillDefinition(String id) {
            this.id = id;
        }
    }

    static class A2aTaskHandler {
        final String messagePattern;
        final String responseText;
        final boolean isError;

        A2aTaskHandler(String messagePattern, String responseText, boolean isError) {
            this.messagePattern = messagePattern;
            this.responseText = responseText;
            this.isError = isError;
        }
    }

    static class WebhookTarget {
        final String host;
        final int port;
        final boolean secure;
        final String path;

        private WebhookTarget(String host, int port, boolean secure, String path) {
            this.host = host;
            this.port = port;
            this.secure = secure;
            this.path = path;
        }

        String hostHeader() {
            return host + ":" + port;
        }

        static WebhookTarget parse(String url) {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            boolean secure = "https".equalsIgnoreCase(scheme);
            String host = uri.getHost();
            if (host == null) {
                throw new IllegalArgumentException("Invalid push-notification webhook URL (no host): " + url);
            }
            int port = uri.getPort();
            if (port == -1) {
                port = secure ? 443 : 80;
            }
            String path = uri.getRawPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            return new WebhookTarget(host, port, secure, path);
        }
    }

    public static class A2aSkillBuilder {
        private final A2aMockBuilder parent;
        private final A2aSkillDefinition skill;

        A2aSkillBuilder(A2aMockBuilder parent, String id) {
            this.parent = parent;
            this.skill = new A2aSkillDefinition(id);
        }

        public A2aSkillBuilder withName(String name) {
            skill.name = name;
            return this;
        }

        public A2aSkillBuilder withDescription(String description) {
            skill.description = description;
            return this;
        }

        public A2aSkillBuilder withTag(String tag) {
            skill.tags.add(tag);
            return this;
        }

        public A2aSkillBuilder withExample(String example) {
            skill.examples.add(example);
            return this;
        }

        public A2aMockBuilder and() {
            parent.skills.add(skill);
            return parent;
        }
    }

    public static class A2aTaskHandlerBuilder {
        private final A2aMockBuilder parent;
        private String messagePattern = ".*";
        private String responseText = "Task completed";
        private boolean isError = false;

        A2aTaskHandlerBuilder(A2aMockBuilder parent) {
            this.parent = parent;
        }

        public A2aTaskHandlerBuilder matchingMessage(String pattern) {
            this.messagePattern = pattern;
            return this;
        }

        public A2aTaskHandlerBuilder respondingWith(String text) {
            this.responseText = text;
            return this;
        }

        public A2aTaskHandlerBuilder respondingWith(String text, boolean isError) {
            this.responseText = text;
            this.isError = isError;
            return this;
        }

        public A2aMockBuilder and() {
            parent.taskHandlers.add(new A2aTaskHandler(messagePattern, responseText, isError));
            return parent;
        }
    }
}
