package org.mockserver.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

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

        expectations.add(buildTasksSendExpectation());
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
            "\"capabilities\": {\"streaming\": false, \"pushNotifications\": false, \"stateTransitionHistory\": false}, " +
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

    private Expectation buildCustomTaskHandler(A2aTaskHandler handler) {
        String escapedPattern = handler.messagePattern.replace("/", "\\/");
        escapedPattern = escapedPattern.replace("\n", "\\n").replace("\r", "\\r").replace("\0", "");
        String jsonPathBody = "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /" + escapedPattern + "/)]";
        String resultJson = buildTaskResultJson(handler.responseText, handler.isError);

        return Expectation.when(
            request().withMethod("POST").withPath(path).withBody(new JsonPathBody(jsonPathBody))
        ).thenRespond(template(VELOCITY, velocityJsonRpcResponse(resultJson)));
    }

    private String buildTaskResultJson(String responseText, boolean isError) {
        String state = isError ? "failed" : "completed";
        return "{\"id\": \"mock-task-id\", " +
            "\"status\": {\"state\": \"" + state + "\"}, " +
            "\"artifacts\": [{\"parts\": [{\"type\": \"text\", \"text\": \"" + escapeVelocity(escapeJson(responseText)) + "\"}]}]}";
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
