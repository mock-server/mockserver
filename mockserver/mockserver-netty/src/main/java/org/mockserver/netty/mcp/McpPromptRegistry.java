package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Registry of MCP prompts exposed by MockServer's own MCP server.
 * <p>
 * Mirrors {@link McpResourceRegistry} and {@link McpToolRegistry}: each prompt is
 * statically configured here with a name, description, an ordered list of
 * {@link PromptArgument}s and a message template. {@code prompts/list} returns the
 * prompt metadata; {@code prompts/get} returns a prompt's messages with the supplied
 * arguments substituted into {@code {{argument}}} placeholders.
 */
public class McpPromptRegistry {

    private final ObjectMapper objectMapper;
    private final Map<String, PromptDefinition> prompts;

    public McpPromptRegistry() {
        this.objectMapper = ObjectMapperFactory.createObjectMapper();
        this.prompts = new LinkedHashMap<>();
        registerAllPrompts();
    }

    public Map<String, PromptDefinition> getPrompts() {
        return prompts;
    }

    /**
     * Appended to every diagnosis prompt so the answer keeps what MockServer reported apart from the
     * assistant's own reasoning: facts are copied from tool results, never reworded or invented.
     */
    static final String FACTS_THEN_INTERPRETATION = "\n\nAnswer in two separate sections. Under the heading " +
        "\"Facts from MockServer\", list only values copied from the tool results (status codes, counts, field names, " +
        "differences and error text), each with the name of the tool that returned it, without rewording them. If a tool " +
        "returned an error or nothing, say so there instead of guessing. Under the heading \"Interpretation\", give your " +
        "explanation and the change you suggest, and present nothing in that section as MockServer output.";

    private void registerAllPrompts() {
        prompts.put("debug_unmatched_request", new PromptDefinition(
            "debug_unmatched_request",
            "Guides debugging of a request that matched no MockServer expectation",
            List.of(
                new PromptArgument("method", "HTTP method of the request that did not match", true),
                new PromptArgument("path", "Path of the request that did not match", true)
            ),
            "user",
            "A {{method}} request to {{path}} did not match any MockServer expectation. " +
                "Call the 'explain_unmatched_requests' tool, then summarise the closest expectation " +
                "and the field-level differences, and suggest a fix." +
                FACTS_THEN_INTERPRETATION
        ));

        prompts.put("why_did_verify_fail", new PromptDefinition(
            "why_did_verify_fail",
            "Explains why a MockServer request verification failed, from the verification result and the requests MockServer received",
            List.of(
                new PromptArgument("method", "HTTP method of the request that was verified", true),
                new PromptArgument("path", "Path of the request that was verified", true)
            ),
            "user",
            "A MockServer verification of {{method}} {{path}} failed. Work through these steps in order.\n" +
                "1. Call 'verify_request' with method \"{{method}}\" and path \"{{path}}\", and with the atLeast and atMost " +
                "counts the failing check used if you know them. Its \"message\" states how many matching requests were " +
                "expected and found, then the requests MockServer received (or how many there were) and, when any were " +
                "received, a \"closest match diff\" naming each field of the closest request that differed. It is evaluated " +
                "against the requests recorded now, so it also counts any that arrived after the original check ran. " +
                "If the failing verification also matched headers, query parameters or a body, call 'raw_verify' with the " +
                "full verification JSON instead.\n" +
                "2. Call 'retrieve_recorded_requests' with method \"{{method}}\" to see every {{method}} request MockServer " +
                "received, and compare their paths, headers and bodies with what was verified.\n" +
                "3. If no request reached MockServer at all, call 'get_status' to confirm it is running and which ports it " +
                "listens on, then call 'retrieve_logs' to look for errors.\n" +
                "Then say whether the code under test sent a different request, sent it a different number of times, or " +
                "the verification itself expects the wrong thing, and suggest the change." +
                FACTS_THEN_INTERPRETATION
        ));

        prompts.put("fix_my_expectation", new PromptDefinition(
            "fix_my_expectation",
            "Finds the smallest change that makes an existing MockServer expectation match a request, starting from the fix MockServer suggests, and applies it only once the user agrees",
            List.of(
                new PromptArgument("method", "HTTP method of the request the expectation should match", true),
                new PromptArgument("path", "Path of the request the expectation should match", true)
            ),
            "user",
            "A {{method}} request to {{path}} is not getting the response the user expects from MockServer. Work through " +
                "these steps in order.\n" +
                "1. Call 'suggest_expectation_fix' with limit 20, adding \"expectationId\" if the user named the expectation they " +
                "meant. " +
                "It covers only requests that reached MockServer and matched nothing. Use only the entries in " +
                "\"unmatchedRequests\" whose \"method\" and \"path\" are {{method}} and {{path}}; each lists close " +
                "expectations by \"expectationId\", each with a \"suggestedFix\": a \"patch\" (an RFC 6902 JSON patch of " +
                "the expectation as 'list_expectations' returns it, each change preceded by a \"test\" of the current " +
                "value), the \"unpatchedFields\" it could not patch with the reason for each, and \"matchesAfterPatch\". " +
                "Nothing is applied.\n" +
                "2. Call 'list_expectations' and take the full JSON of the expectation the user meant (normally the first " +
                "one with a suggested fix) by its id.\n" +
                "3. If there is no suggested fix for that expectation (a \"suggestedFixCount\" of 0 with a \"note\" means " +
                "the request may not have been sent or may have matched another expectation; a fix is suggested only for the " +
                "five closest expectations), or its " +
                "\"matchesAfterPatch\" is false, or \"unpatchedFields\" lists fields, call 'debug_request_mismatch' with " +
                "method \"{{method}}\" and path \"{{path}}\", adding the request's headers and body if the user gave them. " +
                "It returns one result per active expectation, closest first, each with its \"expectationId\", whether it " +
                "\"matches\", the \"differences\" per field and a \"remediation\" hint. If an expectation the user did not " +
                "mean also matches, it may be the one answering the request: say so.\n" +
                "Propose the change: the suggested patch applied to the expectation's JSON when there is one, plus the " +
                "smallest hand-made change to any field it left unpatched, or only a hand-made change when no fix was " +
                "suggested. Show each changed field with its old and new value and say which changes MockServer " +
                "suggested. Do not apply it until the user agrees. Then call 'raw_expectation' with the corrected " +
                "expectation, keeping its \"id\" so it replaces the original rather than adding a second one, and call " +
                "'debug_request_mismatch' again to confirm that it now matches." +
                FACTS_THEN_INTERPRETATION
        ));

        prompts.put("why_forward_failed", new PromptDefinition(
            "why_forward_failed",
            "Explains why MockServer could not forward or proxy a request, from the failed forwards MockServer recorded and the forward expectation",
            List.of(
                new PromptArgument("method", "HTTP method of the request that failed to forward", true),
                new PromptArgument("path", "Path of the request that failed to forward", true)
            ),
            "user",
            "A {{method}} request to {{path}} that MockServer should have forwarded or proxied failed. Work through these " +
                "steps in order.\n" +
                "1. Call 'explain_forward_failure' with limit 20. It returns \"forwardFailures\", most recent first, each " +
                "with its \"correlationId\", \"method\", \"path\", \"statusCode\" (502) and \"reason\"; use the ones whose " +
                "method and path are {{method}} and {{path}}. When \"causeNamed\" is true, the reason is the cause " +
                "MockServer named and what to change (for example a TLS failure, an HTTP/2 error, a connection it could not " +
                "set up, an upstream response it could not decode, an unresolvable host, or a refused or timed-out " +
                "connection), and it is also the 502 body. When \"forwardLoopSuspected\" is true, the forward went back to " +
                "MockServer itself. When \"causeNamed\" is false, the reason is the raw error: call 'retrieve_logs' with " +
                "that \"correlationId\" to see the request's whole lifecycle, including any exception logged for it.\n" +
                "2. Call 'list_expectations' with path \"{{path}}\" to find the expectation that forwarded it and note its " +
                "target host, port and scheme. If none matches, MockServer was acting as a proxy and sent the request to " +
                "the host the request itself named.\n" +
                "3. If \"forwardFailureCount\" is 0, or no failure has this method and path, call " +
                "'retrieve_request_responses' with method \"{{method}}\" and path \"{{path}}\" to see what MockServer " +
                "sent back: a response other than a 502 means the forward did not fail inside MockServer. MockServer " +
                "records failed forwards only when its logLevel is INFO or more detailed (the default is INFO), and drops " +
                "the oldest log entries beyond maxLogEntries, so if neither tool shows the request, report that instead " +
                "of guessing a cause.\n" +
                "Then name the most likely cause and the change to make: to the forward target, the upstream service, " +
                "MockServer's TLS or proxy settings, or the network between them." +
                FACTS_THEN_INTERPRETATION
        ));

        prompts.put("create_mock_from_description", new PromptDefinition(
            "create_mock_from_description",
            "Turns a natural-language description of an endpoint into a MockServer expectation",
            List.of(
                new PromptArgument("description", "Plain-language description of the endpoint to mock", true)
            ),
            "user",
            "Create a MockServer expectation for this endpoint: {{description}}\n\n" +
                "Use 'create_expectation' when the description needs only a method, a path, a status code, a response body, " +
                "response headers, a match count (times), a time to live or a chaos (fault injection) profile; it accepts no " +
                "other parameters. When the request must also match on headers, query parameters, cookies or a body, or the " +
                "response needs a delay or a template, use 'raw_expectation' with the full expectation JSON (an object with " +
                "\"httpRequest\" and \"httpResponse\").\n" +
                "If the tool returns an error, read its \"message\" and, when present, its \"unknownParameters\" or " +
                "\"validationErrors\": each names the exact field to fix. Correct only those fields and call the tool " +
                "again; do not drop details from the description to make the error go away.\n" +
                "Then call 'list_expectations' with the same method and path to confirm the expectation is active. Report " +
                "the expectation id the tool returned, then list separately every detail you assumed because the " +
                "description did not state it (for example the status code or the content type)."
        ));
    }

    /**
     * Build the messages for a named prompt, substituting {@code {{argument}}} placeholders
     * with the supplied argument values. Returns {@code null} when the prompt is unknown.
     *
     * @throws MissingPromptArgumentsException when a required argument is absent or {@code null}
     */
    public ArrayNode getMessages(String name, JsonNode arguments) {
        PromptDefinition prompt = prompts.get(name);
        if (prompt == null) {
            return null;
        }
        List<String> missingArguments = missingRequiredArguments(prompt, arguments);
        if (!missingArguments.isEmpty()) {
            throw new MissingPromptArgumentsException(name, missingArguments);
        }

        String text = prompt.template;
        if (arguments != null && arguments.isObject()) {
            // Collect substitutions first, then apply them in a single left-to-right pass over the
            // template, so an argument value that happens to contain another argument's {{placeholder}}
            // is never itself re-substituted (no cascade injection).
            Map<String, String> substitutions = new HashMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = arguments.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                JsonNode valueNode = entry.getValue();
                String value = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
                substitutions.put(entry.getKey(), value);
            }
            text = applySubstitutions(text, substitutions);
        }

        ArrayNode messages = objectMapper.createArrayNode();
        ObjectNode message = objectMapper.createObjectNode();
        message.put("role", prompt.role);
        ObjectNode content = message.putObject("content");
        content.put("type", "text");
        content.put("text", text);
        messages.add(message);
        return messages;
    }

    private static List<String> missingRequiredArguments(PromptDefinition prompt, JsonNode arguments) {
        List<String> missing = new ArrayList<>();
        for (PromptArgument argument : prompt.arguments) {
            if (argument.required) {
                JsonNode value = arguments != null && arguments.isObject() ? arguments.get(argument.name) : null;
                if (value == null || value.isNull()) {
                    missing.add(argument.name);
                }
            }
        }
        return missing;
    }

    /** Matches a {@code {{name}}} placeholder where {@code name} is a typical argument identifier. */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_]+)}}");

    /**
     * Replace every {@code {{name}}} placeholder in {@code template} with its value from
     * {@code substitutions} in a single left-to-right pass. Placeholders with no matching argument
     * are left untouched; substituted values are inserted literally and never re-scanned for further
     * placeholders.
     */
    private static String applySubstitutions(String template, Map<String, String> substitutions) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = substitutions.containsKey(key) ? substitutions.get(key) : matcher.group(0);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Thrown by {@link #getMessages} when a {@code prompts/get} call omits a required argument, which
     * the MCP prompts specification answers with JSON-RPC error {@code -32602} (invalid params).
     */
    public static class MissingPromptArgumentsException extends IllegalArgumentException {
        private final List<String> missingArguments;

        public MissingPromptArgumentsException(String promptName, List<String> missingArguments) {
            super("Missing required argument" + (missingArguments.size() == 1 ? "" : "s") +
                " for prompt '" + promptName + "': " + String.join(", ", missingArguments));
            this.missingArguments = List.copyOf(missingArguments);
        }

        public List<String> getMissingArguments() {
            return missingArguments;
        }
    }

    public static class PromptArgument {
        private final String name;
        private final String description;
        private final boolean required;

        public PromptArgument(String name, String description, boolean required) {
            this.name = name;
            this.description = description;
            this.required = required;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public boolean isRequired() {
            return required;
        }
    }

    public static class PromptDefinition {
        private final String name;
        private final String description;
        private final List<PromptArgument> arguments;
        private final String role;
        private final String template;

        public PromptDefinition(String name, String description, List<PromptArgument> arguments, String role, String template) {
            this.name = name;
            this.description = description;
            this.arguments = arguments;
            this.role = role;
            this.template = template;
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<PromptArgument> getArguments() {
            return arguments;
        }

        public String getRole() {
            return role;
        }

        public String getTemplate() {
            return template;
        }
    }
}
