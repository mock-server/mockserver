package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.model.Completion;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runtime client for Amazon Bedrock's Anthropic models
 * ({@code POST /model/{modelId}/invoke}). The request body and response shape
 * are Anthropic's (so InvokeModel response parsing is inherited from
 * {@link AnthropicLlmClient}), wrapped with the Bedrock
 * {@code anthropic_version} field. Converse responses are also recognised when
 * parsing, for proxied {@code /converse} traffic.
 * <p>
 * <strong>Auth — SigV4 signing:</strong> When {@link LlmBackend#apiKey()} is set
 * in the format {@code accessKeyId:secretAccessKey} (optionally
 * {@code accessKeyId:secretAccessKey:sessionToken} for STS temporary credentials),
 * this client automatically signs the request with AWS Signature Version 4 using
 * JDK crypto only (SHA-256, HmacSHA256). The region is parsed from the
 * {@code baseUrl} host ({@code bedrock-runtime.<region>.amazonaws.com}) and
 * defaults to {@code us-east-1} if not parseable. The service is {@code bedrock}.
 * <p>
 * <strong>Headers escape hatch:</strong> The {@link LlmBackend#headers()} escape
 * hatch remains supported for pre-signed / signing-proxy setups. When SigV4 creds
 * are present, the auto-generated {@code Authorization} header takes precedence
 * over any {@code Authorization} supplied via the escape hatch (the SigV4 header
 * is applied after the escape-hatch headers). When no creds are present, the
 * escape hatch is the only source of auth — backward compatible with the original
 * behaviour.
 */
public class BedrockLlmClient extends AnthropicLlmClient {

    static final String BEDROCK_ANTHROPIC_VERSION = "bedrock-2023-05-31";
    static final String DEFAULT_MODEL = "anthropic.claude-3-5-sonnet-20241022-v2:0";
    private static final int DEFAULT_MAX_TOKENS = 1024;
    private static final String DEFAULT_REGION = "us-east-1";
    private static final String SERVICE = "bedrock";
    static final String INPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Input-Token-Count";
    static final String OUTPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Output-Token-Count";
    static final String CACHE_READ_INPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Cache-Read-Input-Token-Count";
    static final String CACHE_WRITE_INPUT_TOKEN_COUNT_HEADER = "X-Amzn-Bedrock-Cache-Write-Input-Token-Count";

    /**
     * Pattern to extract the region from a Bedrock runtime endpoint hostname.
     * Matches {@code bedrock-runtime.<region>.amazonaws.com}.
     */
    private static final Pattern BEDROCK_HOST_REGION_PATTERN =
        Pattern.compile("bedrock-runtime\\.([a-z0-9-]+)\\.amazonaws\\.com");
    private static final OpenAiLlmClient OPENAI_USAGE = new OpenAiLlmClient();

    @Override
    public Provider provider() {
        return Provider.BEDROCK;
    }

    @Override
    public HttpRequest buildCompletionRequest(LlmBackend backend, ParsedConversation prompt) {
        // baseUrl is the regional endpoint, e.g. https://bedrock-runtime.us-east-1.amazonaws.com
        String baseUrl = resolveBaseUrl(backend, "https://bedrock-runtime.us-east-1.amazonaws.com");
        String modelId = resolveModel(backend, DEFAULT_MODEL);

        ObjectNode body = OBJECT_MAPPER.createObjectNode();
        body.put("anthropic_version", BEDROCK_ANTHROPIC_VERSION);
        body.put("max_tokens", DEFAULT_MAX_TOKENS);
        body.put("temperature", 0);

        StringBuilder system = new StringBuilder();
        ArrayNode messages = body.putArray("messages");
        for (ParsedMessage message : prompt.getMessages()) {
            String text = message.getTextContent();
            if (text == null || text.isEmpty()) {
                continue;
            }
            if (message.getRole() == ParsedMessage.Role.SYSTEM) {
                if (system.length() > 0) {
                    system.append("\n");
                }
                system.append(text);
            } else {
                ObjectNode messageNode = messages.addObject();
                messageNode.put("role", message.getRole() == ParsedMessage.Role.ASSISTANT ? "assistant" : "user");
                messageNode.put("content", text);
            }
        }
        if (system.length() > 0) {
            body.put("system", system.toString());
        }

        String jsonBody = writeJson(body);

        // Build the base request (escape-hatch headers applied inside postJson)
        HttpRequest request = postJson(backend, baseUrl, "/model/" + modelId + "/invoke", jsonBody);

        // SigV4 signing: parse creds from apiKey in format akid:secret[:token]
        AwsCredentials creds = parseAwsCredentials(backend.apiKey());
        if (creds != null) {
            URI uri = URI.create(baseUrl);
            String host = uri.getHost();
            String region = parseRegion(host);

            // Canonical URI: encode each path segment per SigV4 rules.
            // The path is /model/{modelId}/invoke — modelId may contain ':' and '.'
            String canonicalUri = "/model/" + AwsSigV4Signer.uriEncodePathSegment(modelId) + "/invoke";

            byte[] payloadBytes = jsonBody.getBytes(StandardCharsets.UTF_8);

            Map<String, String> sigHeaders = AwsSigV4Signer.sign(
                "POST",
                host,
                canonicalUri,
                "",  // no query string
                payloadBytes,
                region,
                SERVICE,
                creds.accessKeyId,
                creds.secretAccessKey,
                creds.sessionToken,
                signingTimestamp(),
                Collections.singletonMap("content-type", "application/json")
            );

            // Apply SigV4 headers — these override any same-named headers from
            // the escape hatch (applied earlier in postJson), so SigV4 takes precedence.
            for (Map.Entry<String, String> entry : sigHeaders.entrySet()) {
                request.replaceHeader(new Header(entry.getKey(), entry.getValue()));
            }
        }

        return request;
    }

    /**
     * Parses either Bedrock response shape: the Anthropic body that {@code InvokeModel} returns
     * for Claude (inherited), or the Converse envelope ({@code output.message.content[]},
     * camelCase {@code usage}) that proxied {@code /converse} traffic carries, so token and cost
     * observability work for both APIs. A body whose usage has no token counts, such as an
     * embedding, rerank or non-Anthropic model's, is counted from the token-count headers
     * {@code InvokeModel} returns for every model, or without them from a Titan body's counts.
     */
    @Override
    public Completion parseCompletionResponse(HttpResponse response) {
        JsonNode root = readBody(response);
        Completion completion = parseBody(root, response);
        if (!hasTokenCounts(completion.getUsage())) {
            Usage usage = usageFromHeaders(response);
            if (usage == null) {
                usage = titanUsage(root);
            }
            if (usage != null) {
                completion.withUsage(usage);
            }
        }
        return completion;
    }

    private static boolean hasTokenCounts(Usage usage) {
        return usage != null && (usage.getInputTokens() != null || usage.getOutputTokens() != null);
    }

    /**
     * Titan reports {@code inputTextTokenCount}, and a Titan Text model the output tokens of each
     * result in {@code results[].tokenCount}.
     */
    private static Usage titanUsage(JsonNode root) {
        if (!root.hasNonNull("inputTextTokenCount")) {
            return null;
        }
        Usage usage = Usage.usage().withInputTokens(Math.max(0, root.path("inputTextTokenCount").asInt()));
        long output = 0;
        boolean counted = false;
        for (JsonNode result : root.path("results")) {
            JsonNode tokenCount = result.path("tokenCount");
            if (tokenCount.isIntegralNumber() && tokenCount.canConvertToLong() && tokenCount.asLong() >= 0) {
                output = Math.min(Integer.MAX_VALUE, output + Math.min(Integer.MAX_VALUE, tokenCount.asLong()));
                counted = true;
            }
        }
        return counted ? usage.withOutputTokens((int) output) : usage;
    }

    private static Usage usageFromHeaders(HttpResponse response) {
        Integer input = headerCount(response, INPUT_TOKEN_COUNT_HEADER);
        Integer output = headerCount(response, OUTPUT_TOKEN_COUNT_HEADER);
        if (input == null && output == null) {
            return null;
        }
        Usage usage = Usage.usage().withInputTokens(input).withOutputTokens(output);
        Integer cacheRead = headerCount(response, CACHE_READ_INPUT_TOKEN_COUNT_HEADER);
        if (cacheRead != null) {
            usage.withCachedInputTokens(cacheRead);
        }
        Integer cacheWrite = headerCount(response, CACHE_WRITE_INPUT_TOKEN_COUNT_HEADER);
        if (cacheWrite != null) {
            usage.withCacheCreationTokens(cacheWrite);
        }
        return usage;
    }

    private static Integer headerCount(HttpResponse response, String name) {
        String value = response.getFirstHeader(name);
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            int count = Integer.parseInt(value.trim());
            return count >= 0 ? count : null;
        } catch (NumberFormatException notACount) {
            return null;
        }
    }

    private Completion parseBody(JsonNode root, HttpResponse response) {
        JsonNode message = root.path("output").path("message");
        if (!message.isObject()) {
            return super.parseCompletionResponse(response);
        }
        Completion completion = Completion.completion();
        StringBuilder text = new StringBuilder();
        for (JsonNode block : message.path("content")) {
            if (block.path("text").isTextual()) {
                text.append(block.path("text").asText());
            }
        }
        if (text.length() > 0) {
            completion.withText(text.toString());
        }
        if (root.hasNonNull("stopReason")) {
            completion.withStopReason(root.path("stopReason").asText());
        }
        Usage usage = parseConverseUsage(root.path("usage"));
        if (usage != null) {
            completion.withUsage(usage);
        }
        return completion;
    }

    /**
     * Maps each usage shape: the camelCase object of Converse and of the ConverseStream
     * {@code metadata} event; the OpenAI object of the {@code /openai/v1/chat/completions}
     * endpoint, of its stream's usage chunk and of an OpenAI-format model such as gpt-oss on
     * {@code InvokeModel}; or the Anthropic object of {@code InvokeModel} and of the events inside
     * an {@code InvokeModelWithResponseStream} stream (inherited).
     */
    @Override
    public Usage parseUsage(JsonNode usageNode) {
        if (usageNode != null && (usageNode.has("inputTokens") || usageNode.has("outputTokens"))) {
            return parseConverseUsage(usageNode);
        }
        if (usageNode != null && (usageNode.has("prompt_tokens") || usageNode.has("completion_tokens"))
            && !usageNode.has("input_tokens") && !usageNode.has("output_tokens")) {
            return OPENAI_USAGE.parseUsage(usageNode);
        }
        return super.parseUsage(usageNode);
    }

    private static Usage parseConverseUsage(JsonNode usageNode) {
        if (usageNode == null || !usageNode.isObject()) {
            return null;
        }
        Usage usage = Usage.usage();
        if (usageNode.has("inputTokens")) {
            usage.withInputTokens(usageNode.path("inputTokens").asInt());
        }
        if (usageNode.has("outputTokens")) {
            usage.withOutputTokens(usageNode.path("outputTokens").asInt());
        }
        if (usageNode.has("cacheReadInputTokens")) {
            usage.withCachedInputTokens(usageNode.path("cacheReadInputTokens").asInt());
        }
        if (usageNode.has("cacheWriteInputTokens")) {
            usage.withCacheCreationTokens(usageNode.path("cacheWriteInputTokens").asInt());
        }
        return usage;
    }

    /**
     * Signing timestamp. Overridable for testing (pin the clock to produce
     * deterministic signatures). The default returns {@link Instant#now()}.
     */
    protected Instant signingTimestamp() {
        return Instant.now();
    }

    /**
     * Parse AWS credentials from an apiKey string. The expected format is
     * {@code accessKeyId:secretAccessKey} or {@code accessKeyId:secretAccessKey:sessionToken}.
     *
     * @return parsed credentials, or {@code null} if the apiKey is null, blank,
     *         or does not contain at least one ':' separator.
     */
    static AwsCredentials parseAwsCredentials(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        int firstColon = apiKey.indexOf(':');
        if (firstColon < 0) {
            return null; // not in akid:secret format — skip signing
        }
        String accessKeyId = apiKey.substring(0, firstColon);
        String rest = apiKey.substring(firstColon + 1);
        int secondColon = rest.indexOf(':');
        if (secondColon < 0) {
            return new AwsCredentials(accessKeyId, rest, null);
        } else {
            return new AwsCredentials(
                accessKeyId,
                rest.substring(0, secondColon),
                rest.substring(secondColon + 1)
            );
        }
    }

    /**
     * Extract the AWS region from a Bedrock runtime host. Falls back to
     * {@code us-east-1} if the host does not match the expected pattern.
     */
    static String parseRegion(String host) {
        if (host == null) {
            return DEFAULT_REGION;
        }
        Matcher matcher = BEDROCK_HOST_REGION_PATTERN.matcher(host);
        return matcher.matches() ? matcher.group(1) : DEFAULT_REGION;
    }

    /**
     * Parsed AWS credentials tuple.
     */
    static final class AwsCredentials {
        final String accessKeyId;
        final String secretAccessKey;
        final String sessionToken; // nullable

        AwsCredentials(String accessKeyId, String secretAccessKey, String sessionToken) {
            this.accessKeyId = accessKeyId;
            this.secretAccessKey = secretAccessKey;
            this.sessionToken = sessionToken;
        }
    }
}
