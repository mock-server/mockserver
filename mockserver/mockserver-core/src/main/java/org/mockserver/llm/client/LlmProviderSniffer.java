package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.JsonNode;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;

import java.net.URI;
import java.util.Optional;

/**
 * Maps a forwarded request's target host/URL to an LLM {@link Provider}, enabling
 * GenAI observability on the proxy/forward path. Pure function of its inputs (request
 * host/path, configuration) — no shared mutable state.
 * <p>
 * Detection order:
 * <ol>
 *   <li>Well-known provider hosts (exact or wildcard match), including the OpenAI
 *       Codex backend ({@code chatgpt.com}) used by coding CLIs such as opencode,
 *       which serves the Responses API at {@code /backend-api/codex/responses}</li>
 *   <li>Configured {@code mockserver.llmBaseUrl} Ollama host match</li>
 *   <li>Fallback to configured {@code mockserver.llmProvider} — only when the
 *       request path looks like an LLM endpoint (contains any of
 *       {@code /chat/completions}, {@code /messages}, {@code /completions},
 *       {@code /responses}, {@code /embeddings}, {@code /v1/},
 *       {@code :generatecontent}, {@code /api/generate}, {@code /api/chat})</li>
 * </ol>
 * Returns {@link Optional#empty()} when the request is not LLM traffic.
 */
public final class LlmProviderSniffer {

    private LlmProviderSniffer() {
    }

    /**
     * The live server {@link org.mockserver.configuration.Configuration}, installed by
     * {@code HttpState}'s constructor via {@link #setConfiguration(org.mockserver.configuration.Configuration)}.
     *
     * <p>This class is a wholly-static utility invoked from several INDEPENDENT static chains that
     * carry no configuration of their own — {@code LlmDatasetExporter.export(...)},
     * {@code LlmOptimisationReportBuilder.build(...)}, {@code McpToolRegistry}'s analysis tools, and
     * {@code HttpActionHandler}'s forward path all call {@code sniff}/{@code detectForAnalysis}
     * statically. Threading a {@code Configuration} parameter down every one of those chains would be
     * a wide, mechanical change across call sites that mostly do not otherwise touch configuration,
     * so the value is instead PUSHED IN ONCE at startup — the same approach already used by
     * {@code ChaosAutoHaltMonitor.setConfiguration(...)} for the equivalent
     * {@code Metrics.incrementHttpChaosInjected(...)} static chain.
     *
     * <p>This is exactly the {@code Configuration} instance that {@code PUT /mockserver/configuration}
     * mutates, so provider/base-URL settings applied over the REST config API take effect here. When
     * it is {@code null} (no server started — unit tests, client-only use) every read falls back to
     * the static {@link ConfigurationProperties} store, so system-property/env/file users are
     * unaffected.
     */
    private static volatile org.mockserver.configuration.Configuration installedConfiguration;

    /**
     * Install the live server configuration. Called by the runtime ({@code HttpState}'s constructor);
     * a {@code null} argument is ignored so an already-installed configuration is never cleared.
     */
    public static void setConfiguration(org.mockserver.configuration.Configuration configuration) {
        if (configuration != null) {
            installedConfiguration = configuration;
        }
    }

    /**
     * Clear the installed configuration. Test-support only, so a test that installs a configuration
     * cannot leak it into unrelated tests in the same JVM.
     */
    public static void resetConfiguration() {
        installedConfiguration = null;
    }

    /**
     * Path fragments that indicate an LLM endpoint. Used to gate the configured-provider
     * fallback so non-LLM traffic to unknown hosts is never misclassified.
     * Checked case-insensitively.
     */
    private static final String[] LLM_PATH_FRAGMENTS = {
        "/chat/completions",
        "/messages",
        "/completions",
        "/responses",
        "/embeddings",
        "/v1/",
        ":generatecontent",
        ":streamgeneratecontent",
        "/api/generate",
        "/api/chat",
    };

    /**
     * The endpoints whose response is accounted as an LLM call, matched case-insensitively against
     * the end of the path (any query string removed) so a sub-resource, such as
     * {@code /v1/messages/count_tokens} or {@code /v1/responses/{id}/cancel}, is not.
     */
    private static final java.util.regex.Pattern CHAT_PATH_PATTERN = java.util.regex.Pattern.compile(
        "(?:/completions|/responses(?:/compact)?|/messages|:generatecontent|:streamgeneratecontent|/api/generate|/api/chat|/v[12]/chat"
            + "|/model/([^/]+)/(?:invoke|invoke-with-response-stream|converse|converse-stream)"
            + "|/models/([^/:]+):(?:rawpredict|streamrawpredict))/?$");
    // the completion endpoints whose GenAI operation is not chat
    private static final java.util.regex.Pattern GENERATE_CONTENT_PATH_PATTERN = java.util.regex.Pattern.compile(
        "(?::generatecontent|:streamgeneratecontent)/?$");
    // legacy and FIM /completions (not /chat/completions or Mistral /agents/completions), Ollama /api/generate
    private static final java.util.regex.Pattern TEXT_COMPLETION_PATH_PATTERN = java.util.regex.Pattern.compile(
        "(?:(?<!/chat|/agents)/completions|/api/generate)/?$");
    // Mistral Conversations API: start, append and restart each run the model
    private static final java.util.regex.Pattern MISTRAL_CONVERSATION_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/v1/conversations(?:/[^/]+(?:/restart)?)?/?$");
    // a stored result fetched with GET: an OpenAI background response, an xAI deferred completion
    private static final java.util.regex.Pattern OPENAI_RESPONSE_POLL_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/responses/(?!input_tokens/?$|compact/?$)[^/]+/?$");
    private static final java.util.regex.Pattern XAI_DEFERRED_COMPLETION_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/v1/chat/deferred-completion/[^/]+/?$");
    private static final java.util.Set<String> TERMINAL_RESPONSE_STATUSES = java.util.Set.of("completed", "incomplete", "failed", "cancelled");
    private static final java.util.regex.Pattern EMBEDDINGS_PATH_PATTERN = java.util.regex.Pattern.compile(
        "(?:/embeddings|/multimodalembeddings|/contextualizedembeddings|/api/embed|/v[12]/embed|:embedcontent|:batchembedcontents"
            // Vertex AI embedding models on the publisher-model predict method
            + "|/publishers/google/models/[^/:]*embedding[^/:]*:predict)/?$");
    private static final java.util.regex.Pattern RERANK_PATH_PATTERN = java.util.regex.Pattern.compile("/rerank/?$");
    private static final java.util.regex.Pattern GEMINI_MODEL_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/models/([^/:]+):[A-Za-z]+$");
    private static final java.util.regex.Pattern BEDROCK_MODEL_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/model/([^/]+)/(?:invoke|invoke-with-response-stream|converse|converse-stream)(?:[/?]|$)");
    private static final java.util.regex.Pattern BEDROCK_INVOKE_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/invoke(?:-with-response-stream)?/?$");
    // models whose native InvokeModel request is a prompt string, not chat messages (AWS Bedrock model parameters):
    // Titan Text, AI21 Jurassic-2, Cohere Command and Command Light, Meta Llama, Mistral 7B, Mixtral and Mistral
    // Small 24.02, DeepSeek-R1. Mistral Large 24.02 and Claude 2 accept both, so they are left as chat.
    private static final java.util.regex.Pattern BEDROCK_TEXT_COMPLETION_MODEL_PATTERN = java.util.regex.Pattern.compile(
        "amazon\\.titan-(?:text|tg1)-|ai21\\.j2-|cohere\\.command-(?:light-)?text-|meta\\.llama"
            + "|mistral\\.(?:mistral-7b-instruct|mixtral-8x7b-instruct|mistral-small-2402)|deepseek\\.r1");

    // Path-shape patterns for offline analysis detection (sniffByPath), mirroring
    // the dashboard's llmTraffic.ts. Matched against the original (case-sensitive)
    // path because the model name and method are case-sensitive.
    // Gemini path shapes, including Vertex AI's publisher-model path
    // (/publishers/google/models/<model>:generateContent). The model name and method
    // token are case-sensitive, so this is matched against the original path.
    private static final java.util.regex.Pattern GEMINI_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/v1beta/models/[^/]+:(generateContent|streamGenerateContent)"
            + "|/v1/models/gemini-[^/]+:(generateContent|streamGenerateContent)"
            + "|/publishers/google/models/[^/]+:(generateContent|streamGenerateContent)");
    private static final java.util.regex.Pattern OLLAMA_CHAT_PATTERN = java.util.regex.Pattern.compile(
        "(^|/)api/chat(/?$|\\?)");
    // OpenAI Responses API path: the standard hosted path (/v1/responses, also Azure's
    // /openai/v1/responses), Azure's dated-version /openai/responses, and the OpenAI
    // Codex backend used by coding CLIs such as opencode, which serves the same
    // Responses wire format at chatgpt.com/backend-api/codex/responses. The
    // codex/responses and openai/responses arms are anchored to a path-segment
    // boundary so /mycodex/responses cannot match. Matched case-insensitively
    // (callers lower-case the path before matching).
    private static final java.util.regex.Pattern OPENAI_RESPONSES_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/v1/responses|(^|/)(codex|openai)/responses(?:[/?]|$)");
    // Vertex AI host: global aiplatform.googleapis.com, regional <region>-aiplatform.googleapis.com
    // or multi-region aiplatform.<us|eu>.rep.googleapis.com. Matched whole (no label has a dot) so
    // an arbitrary host cannot be glued on.
    private static final java.util.regex.Pattern VERTEX_HOST_PATTERN = java.util.regex.Pattern.compile(
        "(?:[a-z0-9-]+-)?aiplatform\\.googleapis\\.com|aiplatform\\.[a-z0-9-]+\\.rep\\.googleapis\\.com");
    // Vertex AI's OpenAI-compatible APIs: endpoints/openapi for Model Garden and partner models, and
    // endpoints/{id} for a self-deployed model
    private static final java.util.regex.Pattern VERTEX_OPENAI_COMPATIBLE_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/endpoints/[^/]+/(chat/completions|responses)(?:[/?]|$)");
    // Bedrock Runtime invocation paths: any model id, both invoke and Converse APIs
    // (invoke, invoke-with-response-stream, converse, converse-stream). Not anchored
    // to anthropic.* — non-Anthropic models use the same path shape.
    private static final java.util.regex.Pattern BEDROCK_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/model/[^/]+/(invoke|invoke-with-response-stream|converse|converse-stream)");
    // Cohere rerank (/v2/rerank, /v1/rerank) and chat (/v1/chat as a terminal segment,
    // NOT /v1/chat/completions). Path-only /v1/rerank is shared with Voyage, so on an
    // unknown host it defaults to Cohere; the host check disambiguates the two.
    private static final java.util.regex.Pattern COHERE_PATH_PATTERN = java.util.regex.Pattern.compile(
        "/v[12]/rerank|/v1/chat(/?$|\\?)");
    // Body-shape markers requiring the JSON value to actually be an array, so a
    // string-valued field (e.g. embeddings "input":"hi") does not match.
    private static final java.util.regex.Pattern INPUT_ARRAY_PATTERN = java.util.regex.Pattern.compile(
        "\"input\"\\s*:\\s*\\[");
    private static final java.util.regex.Pattern MESSAGES_ARRAY_PATTERN = java.util.regex.Pattern.compile(
        "\"messages\"\\s*:\\s*\\[");

    /**
     * Sniff the LLM provider from a forwarded request's target host.
     *
     * @param forwardedRequest the request that was forwarded to the upstream
     * @return the detected provider, or empty if this is not LLM traffic
     */
    public static Optional<Provider> sniff(HttpRequest forwardedRequest) {
        if (forwardedRequest == null) {
            return Optional.empty();
        }
        String path = forwardedRequest.getPath() != null
            ? forwardedRequest.getPath().getValue() : null;
        return sniffByHostAndPath(extractHost(forwardedRequest), path);
    }

    /**
     * Detect the LLM provider for OFFLINE analysis of already-captured traffic
     * (e.g. the optimisation report). Recognises LLM traffic that {@link #sniff}
     * cannot — most importantly MOCKED traffic served by MockServer itself on
     * localhost, where there is no upstream provider host. Tries the host-based
     * {@link #sniff} first, then falls back to {@link #sniffByPath}, which mirrors
     * the dashboard's client-side path detection so the SAME traffic appears in
     * both the Sessions view and the optimisation report.
     *
     * <p>Intended only for read-only analysis. The live forward/span path must
     * keep using host-gated {@link #sniff} so forwarded non-LLM traffic is never
     * mis-classified.
     */
    public static Optional<Provider> detectForAnalysis(HttpRequest request) {
        return detectForAnalysis(request, null);
    }

    /**
     * Detect the LLM provider for OFFLINE analysis, using the response body as an
     * additional, more resilient signal. Detection order, cheapest/most-specific
     * first:
     * <ol>
     *   <li>well-known host ({@link #sniff})</li>
     *   <li>recognised URL path shape ({@link #sniffByPath})</li>
     *   <li><b>request/response body shape</b> ({@link #sniffByBodyShape}) — the
     *       resilient fallback that recognises LLM traffic from the wire format
     *       itself, so a coding CLI that routes through an unknown host or a
     *       non-standard path (e.g. a future endpoint rename, a private gateway,
     *       a new tool) is still classified without a code change.</li>
     * </ol>
     * The body shape is the slowest-moving signal — it is the provider's API
     * contract — so keying on it (rather than only on host/path, the dimension
     * that varies most between tools and versions) is what keeps capture working
     * as the LLM APIs and CLI harnesses evolve.
     */
    public static Optional<Provider> detectForAnalysis(HttpRequest request, org.mockserver.model.HttpResponse response) {
        Optional<Provider> byHost = sniff(request);
        if (byHost.isPresent()) {
            return byHost;
        }
        Optional<Provider> byPath = sniffByPath(request);
        if (byPath.isPresent()) {
            return byPath;
        }
        return sniffByBodyShape(request, response);
    }

    /**
     * Provider detection from the request/response <em>body shape</em> alone — no
     * host or path required. This is the resilient fallback for {@link
     * #detectForAnalysis}: the wire format is the provider's API contract and moves
     * far more slowly than the host/path a given CLI happens to use, so recognising
     * LLM traffic by its body keeps the Traffic / LLM Traces / LLM Optimise views
     * working when a tool changes endpoints or a new tool appears.
     *
     * <p>Keys on the most stable, provider-distinctive markers and stays
     * conservative (returns empty rather than guess) so non-LLM traffic is not
     * mis-classified. Read-only analysis use only — never the live forward path.
     */
    public static Optional<Provider> sniffByBodyShape(HttpRequest request, org.mockserver.model.HttpResponse response) {
        // Response markers are the most distinctive — check them first.
        String resBody = response != null ? safeBody(response.getBodyAsText()) : null;
        if (resBody != null) {
            // OpenAI Chat Completions: object "chat.completion" / "chat.completion.chunk".
            // Require the JSON value's opening quote so a stray substring can't match.
            if (resBody.contains("\"chat.completion")) {
                return Optional.of(Provider.OPENAI);
            }
            // OpenAI Responses API: response.* streaming events or object "response".
            if (resBody.contains("response.output_text")
                || resBody.contains("response.created")
                || resBody.contains("response.completed")
                || resBody.contains("\"object\":\"response\"")
                || resBody.contains("\"object\": \"response\"")) {
                return Optional.of(Provider.OPENAI_RESPONSES);
            }
            // Anthropic Messages: streaming content_block / message_start, or a
            // non-streamed message envelope with a stop_reason.
            if (resBody.contains("content_block")
                || resBody.contains("message_start")
                || (resBody.contains("\"type\":\"message\"") && resBody.contains("stop_reason"))) {
                return Optional.of(Provider.ANTHROPIC);
            }
            // Gemini: candidates[] plus usage metadata.
            if (resBody.contains("\"candidates\"") && resBody.contains("usageMetadata")) {
                return Optional.of(Provider.GEMINI);
            }
            // Cohere rerank: results[] with a relevance_score per document.
            if (resBody.contains("relevance_score") && resBody.contains("\"results\"")) {
                return Optional.of(Provider.COHERE);
            }
        }

        // Request markers — used when the response is absent or unrecognised.
        if (request == null) {
            return Optional.empty();
        }
        // Anthropic requires the anthropic-version header on every request.
        String anthropicVersion = request.getFirstHeader("anthropic-version");
        if (anthropicVersion != null && !anthropicVersion.isEmpty()) {
            return Optional.of(Provider.ANTHROPIC);
        }
        String reqBody = safeBody(request.getBodyAsText());
        if (reqBody == null) {
            return Optional.empty();
        }
        boolean hasModel = reqBody.contains("\"model\"");
        // A "messages" ARRAY (not a bare substring) is the Chat Completions / Anthropic
        // marker; require the opening bracket so a stray "messages" value can't match.
        boolean hasMessagesArray = MESSAGES_ARRAY_PATTERN.matcher(reqBody).find();
        // OpenAI Responses: the hallmark top-level "input" ARRAY of message items, or a
        // Responses-distinctive field. A STRING-valued "input" is OpenAI embeddings /
        // moderations ({"model":...,"input":"hi"}) and must NOT match here.
        boolean inputArray = INPUT_ARRAY_PATTERN.matcher(reqBody).find();
        boolean responsesDistinctive = reqBody.contains("\"instructions\"")
            || reqBody.contains("\"previous_response_id\"");
        if (hasModel && !hasMessagesArray && (inputArray || responsesDistinctive)) {
            return Optional.of(Provider.OPENAI_RESPONSES);
        }
        // Gemini: top-level "contents" array (+ a Gemini-specific companion field).
        if (reqBody.contains("\"contents\"")
            && (reqBody.contains("\"parts\"") || reqBody.contains("generationConfig"))) {
            return Optional.of(Provider.GEMINI);
        }
        // OpenAI Chat Completions: model + a messages array. (Anthropic is already
        // caught by its required header and by the response-shape check above.)
        if (hasModel && hasMessagesArray) {
            return Optional.of(Provider.OPENAI);
        }
        // Cohere rerank request: a query plus the documents to score.
        if (reqBody.contains("\"query\"") && reqBody.contains("\"documents\"")) {
            return Optional.of(Provider.COHERE);
        }
        return Optional.empty();
    }

    private static String safeBody(String body) {
        return body == null || body.isEmpty() ? null : body;
    }

    /**
     * Path-shape based provider detection, mirroring the dashboard's
     * {@code llmTraffic.ts} ordering (Anthropic, Azure, Bedrock, OpenAI Responses,
     * OpenAI, Gemini, Ollama). Used to analyse captured traffic whose host is not a
     * known provider (e.g. mocked LLM responses on localhost).
     */
    public static Optional<Provider> sniffByPath(HttpRequest request) {
        if (request == null || request.getPath() == null || request.getPath().getValue() == null) {
            return Optional.empty();
        }
        String path = request.getPath().getValue();
        String lower = path.toLowerCase();
        if (lower.contains("/v1/messages")) {
            return Optional.of(Provider.ANTHROPIC);
        }
        if (lower.contains("/openai/deployments/") && lower.contains("/chat/completions")) {
            return Optional.of(Provider.AZURE_OPENAI);
        }
        if (BEDROCK_PATH_PATTERN.matcher(lower).find()) {
            return Optional.of(Provider.BEDROCK);
        }
        if (OPENAI_RESPONSES_PATH_PATTERN.matcher(lower).find()) {
            return Optional.of(Provider.OPENAI_RESPONSES);
        }
        if (lower.contains("/chat/completions")) {
            return Optional.of(Provider.OPENAI);
        }
        if (GEMINI_PATH_PATTERN.matcher(path).find()) {
            return Optional.of(Provider.GEMINI);
        }
        // Cohere rerank/chat. /v1/rerank is shared with Voyage; on a path alone (no
        // host) it defaults to Cohere — the host check distinguishes the two.
        if (COHERE_PATH_PATTERN.matcher(lower).find()) {
            return Optional.of(Provider.COHERE);
        }
        if (OLLAMA_CHAT_PATTERN.matcher(path).find()) {
            return Optional.of(Provider.OLLAMA);
        }
        return Optional.empty();
    }

    /**
     * Sniff the LLM provider from an explicit host string (for unit testing or
     * callers that already have the host extracted). Equivalent to
     * {@code sniffByHostAndPath(host, null)} — the configured-provider fallback
     * is only applied when the path looks like an LLM endpoint, so with a null
     * path the fallback is skipped.
     */
    public static Optional<Provider> sniffByHost(String host) {
        return sniffByHostAndPath(host, null);
    }

    /**
     * Sniff the LLM provider from an explicit host and path.
     *
     * @param host the target host (may be null)
     * @param path the request path (may be null) — used only for the
     *             configured-provider fallback gate
     */
    public static Optional<Provider> sniffByHostAndPath(String host, String path) {
        if (host == null || host.isEmpty()) {
            return fallbackToConfiguredProvider(path);
        }
        String lowerHost = host.toLowerCase();

        // Well-known provider hosts — no path gate required
        if (lowerHost.equals("api.openai.com")) {
            // Distinguish the OpenAI Responses API (/responses) from the
            // Chat Completions API so LlmClientRegistry uses the correct parser
            if (path != null && path.toLowerCase().contains("/responses")) {
                return Optional.of(Provider.OPENAI_RESPONSES);
            }
            return Optional.of(Provider.OPENAI);
        }
        if (isAzureOpenAiHost(lowerHost, path)) {
            // Azure also serves the Responses API, whose usage is input_tokens/output_tokens
            if (path != null && OPENAI_RESPONSES_PATH_PATTERN.matcher(path.toLowerCase()).find()) {
                return Optional.of(Provider.OPENAI_RESPONSES);
            }
            return Optional.of(Provider.AZURE_OPENAI);
        }
        if (lowerHost.equals("api.anthropic.com")) {
            return Optional.of(Provider.ANTHROPIC);
        }
        // OpenAI Codex backend used by coding CLIs (e.g. opencode): chatgpt.com serves
        // the Responses API at /backend-api/codex/responses. Path-gated so non-LLM
        // chatgpt.com traffic (oauth, account, etc.) is never misclassified.
        if (lowerHost.equals("chatgpt.com") || lowerHost.endsWith(".chatgpt.com")) {
            if (path != null && OPENAI_RESPONSES_PATH_PATTERN.matcher(path.toLowerCase()).find()) {
                return Optional.of(Provider.OPENAI_RESPONSES);
            }
        }
        if (lowerHost.equals("generativelanguage.googleapis.com")) {
            return Optional.of(Provider.GEMINI);
        }
        // Vertex AI: matches() anchors the whole string, so an arbitrary host cannot be glued on.
        // The provider is the wire API the path speaks, Gemini's unless the path says otherwise.
        if (VERTEX_HOST_PATTERN.matcher(lowerHost).matches()) {
            String lowerPath = path != null ? path.toLowerCase() : "";
            java.util.regex.Matcher openAiCompatible = VERTEX_OPENAI_COMPATIBLE_PATH_PATTERN.matcher(lowerPath);
            if (openAiCompatible.find()) {
                return Optional.of("responses".equals(openAiCompatible.group(1)) ? Provider.OPENAI_RESPONSES : Provider.OPENAI);
            }
            // Claude speaks the Anthropic Messages API, Mistral models Mistral's chat API, AI21 Jamba OpenAI's chat shape
            if (lowerPath.contains("/publishers/anthropic/")) {
                return Optional.of(Provider.ANTHROPIC);
            }
            if (lowerPath.contains("/publishers/mistralai/")) {
                return Optional.of(Provider.MISTRAL);
            }
            if (lowerPath.contains("/publishers/ai21/")) {
                return Optional.of(Provider.OPENAI);
            }
            return Optional.of(Provider.GEMINI);
        }
        if (lowerHost.equals("api.cohere.com")) {
            return Optional.of(Provider.COHERE);
        }
        if (lowerHost.equals("api.voyageai.com")) {
            return Optional.of(Provider.VOYAGE);
        }
        // OpenAI-chat-compatible providers — identical Chat Completions wire format on
        // their own host, so the host is the only distinguishing signal (path is the
        // shared /chat/completions). Classifying them here means proxy observability
        // records their traffic as LLM (with provider-correct pricing) instead of
        // dropping it as non-LLM.
        if (lowerHost.equals("api.mistral.ai")) {
            return Optional.of(Provider.MISTRAL);
        }
        if (lowerHost.equals("api.x.ai")) {
            return Optional.of(Provider.XAI);
        }
        if (lowerHost.equals("api.deepseek.com")) {
            return Optional.of(Provider.DEEPSEEK);
        }
        if (lowerHost.equals("api.groq.com")) {
            return Optional.of(Provider.GROQ);
        }
        if (lowerHost.equals("openrouter.ai")) {
            return Optional.of(Provider.OPENROUTER);
        }
        if (lowerHost.equals("api.orcarouter.ai")) {
            return Optional.of(Provider.ORCAROUTER);
        }
        if (isBedrockHost(lowerHost)) {
            return Optional.of(Provider.BEDROCK);
        }

        // Configured Ollama host: match against mockserver.llmBaseUrl
        org.mockserver.configuration.Configuration config = installedConfiguration;
        String configuredBaseUrl = config != null
            ? config.llmBaseUrl()
            : ConfigurationProperties.llmBaseUrl();
        if (configuredBaseUrl != null && !configuredBaseUrl.isEmpty()) {
            String configuredHost = extractHostFromUrl(configuredBaseUrl);
            if (configuredHost != null && lowerHost.equals(configuredHost.toLowerCase())) {
                return Optional.of(Provider.OLLAMA);
            }
        }

        // Fallback: configured mockserver.llmProvider — path-gated
        return fallbackToConfiguredProvider(path);
    }

    /**
     * Whether a forwarded request went to a Vertex AI host, which bills at its own list prices.
     *
     * @param targetHost the upstream host when the forwarded request does not name it, or null
     */
    public static boolean isVertexAiHost(HttpRequest forwardedRequest, String targetHost) {
        String host = targetHost != null ? targetHost : (forwardedRequest != null ? extractHost(forwardedRequest) : null);
        return host != null && VERTEX_HOST_PATTERN.matcher(host.toLowerCase(java.util.Locale.ROOT)).matches();
    }

    /**
     * An Azure OpenAI endpoint: any {@code *.openai.azure.com} host, or the
     * {@code *.services.ai.azure.com} Foundry host on an {@code /openai/} path (that host
     * also serves non-OpenAI APIs).
     */
    public static boolean isAzureOpenAiHost(String lowerHost, String path) {
        return lowerHost.endsWith(".openai.azure.com")
            || (lowerHost.endsWith(".services.ai.azure.com") && path != null && path.toLowerCase().startsWith("/openai/"));
    }

    /**
     * Check if the host matches the Bedrock pattern: the host starts with "bedrock"
     * or contains ".bedrock" and ends with ".amazonaws.com".
     * Examples: bedrock-runtime.us-east-1.amazonaws.com, bedrock.eu-west-1.amazonaws.com
     */
    private static boolean isBedrockHost(String lowerHost) {
        if (!lowerHost.endsWith(".amazonaws.com")) {
            return false;
        }
        return lowerHost.startsWith("bedrock") || lowerHost.contains(".bedrock");
    }

    /**
     * Fallback to the configured {@code mockserver.llmProvider}, but only when
     * the request path looks like an LLM endpoint. This prevents a forward to
     * e.g. {@code example.com/api/users} from being misclassified as LLM traffic
     * just because a provider is configured.
     */
    private static Optional<Provider> fallbackToConfiguredProvider(String path) {
        if (!looksLikeLlmPath(path)) {
            return Optional.empty();
        }
        org.mockserver.configuration.Configuration config = installedConfiguration;
        String configured = config != null
            ? config.llmProvider()
            : ConfigurationProperties.llmProvider();
        if (configured != null && !configured.isEmpty()) {
            try {
                return Optional.of(Provider.valueOf(configured.toUpperCase()));
            } catch (IllegalArgumentException ignored) {
                // unrecognised provider name — not LLM traffic
            }
        }
        return Optional.empty();
    }

    /**
     * Returns {@code true} when the path (case-insensitive) contains any of the
     * well-known LLM endpoint fragments.
     */
    static boolean looksLikeLlmPath(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String lowerPath = path.toLowerCase();
        for (String fragment : LLM_PATH_FRAGMENTS) {
            if (lowerPath.contains(fragment)) {
                return true;
            }
        }
        return BEDROCK_PATH_PATTERN.matcher(lowerPath).find();
    }

    /**
     * The kind of LLM call a response is accounted as, with its OpenTelemetry GenAI
     * {@code gen_ai.operation.name}: a well-known value where one applies (Gemini
     * {@code generateContent} is {@code generate_content}, a legacy or FIM completion is
     * {@code text_completion}). Rerank has no well-known value, so it uses a custom one.
     */
    public enum Operation {
        CHAT("chat"),
        GENERATE_CONTENT("generate_content"),
        TEXT_COMPLETION("text_completion"),
        EMBEDDINGS("embeddings"),
        RERANK("rerank");

        private final String genAiOperationName;

        Operation(String genAiOperationName) {
            this.genAiOperationName = genAiOperationName;
        }

        public String genAiOperationName() {
            return genAiOperationName;
        }

        /** Whether this operation generates a completion, so its response may be a stream. */
        public boolean isCompletion() {
            return this == CHAT || this == GENERATE_CONTENT || this == TEXT_COMPLETION;
        }
    }

    /**
     * The completion operation a forwarded request to a completion endpoint makes, by its path:
     * {@code :generateContent}/{@code :streamGenerateContent} is {@link Operation#GENERATE_CONTENT};
     * a legacy or FIM {@code /completions} and Ollama {@code /api/generate} are
     * {@link Operation#TEXT_COMPLETION}; every other completion endpoint is {@link Operation#CHAT}.
     */
    private static Operation completionOperation(String lowerPath) {
        if (GENERATE_CONTENT_PATH_PATTERN.matcher(lowerPath).find()) {
            return Operation.GENERATE_CONTENT;
        }
        return TEXT_COMPLETION_PATH_PATTERN.matcher(lowerPath).find() ? Operation.TEXT_COMPLETION : Operation.CHAT;
    }

    /**
     * The completion operation of a mocked completion, from the wire API its codec serves: every
     * {@link Provider#GEMINI} completion is a {@code generateContent} response, whatever the path;
     * for any other provider a legacy or FIM {@code /completions} or Ollama {@code /api/generate}
     * request is a text completion, and everything else is a chat completion.
     */
    public static Operation servedCompletionOperation(Provider provider, String path) {
        if (provider == Provider.GEMINI) {
            return Operation.GENERATE_CONTENT;
        }
        String lowerPath = path == null ? "" : withoutQuery(path.toLowerCase(java.util.Locale.ROOT));
        return TEXT_COMPLETION_PATH_PATTERN.matcher(lowerPath).find() ? Operation.TEXT_COMPLETION : Operation.CHAT;
    }

    /**
     * The LLM call a request to an LLM host makes, if it is one whose response is accounted: a
     * {@code POST} to a completion, embeddings or rerank endpoint, or a {@code GET} of a completion
     * that ran in the background ({@link #isPolledCompletion}). Every other endpoint, such as
     * {@code GET /v1/models}, a token count, a batch or Ollama's {@code /api/pull}, is not a model call.
     *
     * @param provider the provider the request was sniffed as, or null
     * @param method   the request method, or null when unknown
     */
    public static Optional<Operation> accountableOperation(Provider provider, String method, String path) {
        if (path == null || path.isEmpty()) {
            return Optional.empty();
        }
        String lowerPath = withoutQuery(path.toLowerCase(java.util.Locale.ROOT));
        if (method != null && !method.isEmpty() && !"POST".equalsIgnoreCase(method)) {
            return isPolledCompletion(provider, method, path) ? Optional.of(Operation.CHAT) : Optional.empty();
        }
        java.util.regex.Matcher chat = CHAT_PATH_PATTERN.matcher(lowerPath);
        if (chat.find()) {
            String bedrockModel = chat.group(1);
            if (bedrockModel != null) {
                // Bedrock invokes embedding and rerank models on the same path as chat models
                if (bedrockModel.contains("rerank")) {
                    return Optional.of(Operation.RERANK);
                }
                if (bedrockModel.contains("embed")) {
                    return Optional.of(Operation.EMBEDDINGS);
                }
                if (isBedrockTextCompletionInvocation(lowerPath)) {
                    return Optional.of(Operation.TEXT_COMPLETION);
                }
            }
            // Claude on Vertex AI counts tokens through a model named count-tokens
            if ("count-tokens".equals(chat.group(2))) {
                return Optional.empty();
            }
            // the deprecated Assistants API adds a message to a thread without calling a model
            return lowerPath.contains("/threads/") ? Optional.empty() : Optional.of(completionOperation(lowerPath));
        }
        if (EMBEDDINGS_PATH_PATTERN.matcher(lowerPath).find()) {
            return Optional.of(Operation.EMBEDDINGS);
        }
        if (RERANK_PATH_PATTERN.matcher(lowerPath).find()) {
            return Optional.of(Operation.RERANK);
        }
        // OpenAI's /v1/conversations creates a conversation object without calling a model
        if (provider == Provider.MISTRAL && MISTRAL_CONVERSATION_PATH_PATTERN.matcher(lowerPath).find()) {
            return Optional.of(Operation.CHAT);
        }
        return Optional.empty();
    }

    /**
     * Whether a Bedrock InvokeModel request ({@code invoke} or {@code invoke-with-response-stream}, not the
     * Converse API, which is chat for every model) is to a model whose native request is a prompt string.
     */
    private static boolean isBedrockTextCompletionInvocation(String lowerPath) {
        if (!BEDROCK_INVOKE_PATH_PATTERN.matcher(lowerPath).find()) {
            return false;
        }
        String modelId = extractBedrockModelFromPath(lowerPath);
        return modelId != null && BEDROCK_TEXT_COMPLETION_MODEL_PATTERN.matcher(modelId).lookingAt();
    }

    /**
     * Whether the request fetches with {@code GET} the result of a completion that ran in the
     * background, whose usage is reported only there: an OpenAI Responses API response created with
     * {@code background: true}, or an xAI deferred chat completion. The response must still be checked
     * with {@link #polledCompletionId} because the same path also retrieves ordinary stored responses.
     */
    public static boolean isPolledCompletion(Provider provider, String method, String path) {
        if (path == null || !"GET".equalsIgnoreCase(method)) {
            return false;
        }
        String lowerPath = withoutQuery(path.toLowerCase(java.util.Locale.ROOT));
        if (provider == Provider.OPENAI_RESPONSES) {
            return OPENAI_RESPONSE_POLL_PATH_PATTERN.matcher(lowerPath).find();
        }
        return provider == Provider.XAI && XAI_DEFERRED_COMPLETION_PATH_PATTERN.matcher(lowerPath).find();
    }

    /**
     * The id of a polled completion ({@link #isPolledCompletion}) whose usage is to be counted: an
     * OpenAI response created in the background that has finished, or an xAI deferred completion,
     * carrying a usage object. A response not created in the background was counted when it was
     * created, so it is not counted again.
     *
     * @return the id, or null when this response is not counted
     */
    public static String polledCompletionId(Provider provider, String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode root =
                org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().readTree(responseBody);
            if (root == null || !root.path("id").isTextual() || root.path("id").asText().isEmpty() || !root.path("usage").isObject()) {
                return null;
            }
            if (provider == Provider.OPENAI_RESPONSES) {
                boolean finishedInBackground = root.path("background").asBoolean(false)
                    && TERMINAL_RESPONSE_STATUSES.contains(root.path("status").asText(""));
                return finishedInBackground ? root.path("id").asText() : null;
            }
            return provider == Provider.XAI ? root.path("id").asText() : null;
        } catch (Exception notJson) {
            return null;
        }
    }

    private static String withoutQuery(String path) {
        int query = path.indexOf('?');
        return query >= 0 ? path.substring(0, query) : path;
    }

    /**
     * The model a Gemini request names in its path ({@code /v1beta/models/{model}:embedContent} and
     * the other model methods), for a response that does not name it, such as an embeddings response.
     *
     * @return the model, or null when the path names none
     */
    public static String extractGeminiModelFromPath(String path) {
        if (path == null) {
            return null;
        }
        int query = path.indexOf('?');
        java.util.regex.Matcher matcher = GEMINI_MODEL_PATH_PATTERN.matcher(query >= 0 ? path.substring(0, query) : path);
        return matcher.find() ? matcher.group(1) : null;
    }

    /**
     * The model a Bedrock Runtime request names in its path ({@code /model/{modelId}/converse} and
     * the other invocation paths), URL-decoded. A foundation-model or inference-profile ARN is reduced
     * to its id, and a cross-Region inference profile id ({@code us.anthropic.claude-...}) to the
     * foundation model id it names, so it can be priced. Any other id, such as an application
     * inference profile ARN, is returned as given.
     *
     * @return the model id, or null when the path is not a Bedrock invocation path
     */
    public static String extractBedrockModelFromPath(String path) {
        if (path == null) {
            return null;
        }
        java.util.regex.Matcher matcher = BEDROCK_MODEL_PATH_PATTERN.matcher(path);
        if (!matcher.find()) {
            return null;
        }
        String modelId;
        try {
            modelId = java.net.URLDecoder.decode(matcher.group(1).replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformed) {
            modelId = matcher.group(1);
        }
        return bedrockModelId(modelId);
    }

    /**
     * The model a Bedrock Agent Runtime {@code /rerank} request names, which it does only in the
     * body, as {@code rerankingConfiguration.bedrockRerankingConfiguration.modelConfiguration.modelArn},
     * reduced as {@link #extractBedrockModelFromPath} reduces a model id.
     *
     * @return the model id, or null when the request names none
     */
    public static String extractBedrockRerankModelFromRequest(HttpRequest request) {
        if (request == null) {
            return null;
        }
        try {
            String body = request.getBodyAsText();
            if (body == null || body.isEmpty()) {
                return null;
            }
            JsonNode modelArn = org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().readTree(body)
                .path("rerankingConfiguration").path("bedrockRerankingConfiguration").path("modelConfiguration").path("modelArn");
            return modelArn.isTextual() ? bedrockModelId(modelArn.asText()) : null;
        } catch (Exception notJson) {
            return null;
        }
    }

    private static String bedrockModelId(String modelId) {
        if (modelId.isEmpty()) {
            return null;
        }
        if (modelId.startsWith("arn:")) {
            int slash = modelId.lastIndexOf('/');
            String resource = slash > 0 ? modelId.substring(modelId.lastIndexOf(':', slash) + 1, slash) : "";
            if (!resource.equals("foundation-model") && !resource.equals("inference-profile")) {
                return modelId;
            }
            modelId = modelId.substring(slash + 1);
        }
        // a foundation model id has one dot (provider.model); a geographic prefix adds a second
        int versionColon = modelId.indexOf(':');
        String beforeVersion = versionColon < 0 ? modelId : modelId.substring(0, versionColon);
        int firstDot = beforeVersion.indexOf('.');
        if (firstDot > 0 && beforeVersion.indexOf('.', firstDot + 1) > 0) {
            modelId = modelId.substring(firstDot + 1);
        }
        return modelId;
    }

    /**
     * Extract the host from the forwarded request. Prefers the socket address
     * host (set by the forward/proxy path), falling back to the Host header,
     * then the request path if it looks like a URL.
     */
    private static String extractHost(HttpRequest request) {
        // Socket address is the most reliable source on the forward path
        if (request.getSocketAddress() != null
            && request.getSocketAddress().getHost() != null
            && !request.getSocketAddress().getHost().isEmpty()) {
            return stripPort(request.getSocketAddress().getHost());
        }
        // Host header
        String hostHeader = request.getFirstHeader("Host");
        if (hostHeader != null && !hostHeader.isEmpty()) {
            return stripPort(hostHeader);
        }
        return null;
    }

    private static String extractHostFromUrl(String url) {
        try {
            URI uri = URI.create(url);
            return uri.getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private static String stripPort(String hostMaybeWithPort) {
        // Handle IPv6 addresses in brackets
        if (hostMaybeWithPort.startsWith("[")) {
            int closeBracket = hostMaybeWithPort.indexOf(']');
            if (closeBracket >= 0) {
                return hostMaybeWithPort.substring(1, closeBracket);
            }
        }
        int colon = hostMaybeWithPort.lastIndexOf(':');
        if (colon > 0) {
            return hostMaybeWithPort.substring(0, colon);
        }
        return hostMaybeWithPort;
    }

    /**
     * Extract the model name from the forwarded response body. Providers typically
     * include a {@code "model"} field in their JSON response (OpenAI, Anthropic,
     * Gemini use this pattern). Returns null if extraction fails.
     */
    public static String extractModelFromResponse(org.mockserver.model.HttpResponse response) {
        if (response == null) {
            return null;
        }
        try {
            String body = response.getBodyAsText();
            if (body == null || body.isEmpty()) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode root =
                org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().readTree(body);
            if (root != null && root.has("model")) {
                String model = root.path("model").asText();
                if (model != null && !model.isEmpty()) {
                    return model;
                }
            }
        } catch (Exception ignored) {
            // fail-soft: not parseable as JSON or no model field
        }
        return null;
    }

    /**
     * Extract the model name from the forwarded request body. Providers typically
     * include a {@code "model"} field in their JSON request body. Returns null if
     * extraction fails.
     */
    public static String extractModelFromRequest(HttpRequest request) {
        if (request == null) {
            return null;
        }
        try {
            String body = request.getBodyAsText();
            if (body == null || body.isEmpty()) {
                return null;
            }
            com.fasterxml.jackson.databind.JsonNode root =
                org.mockserver.serialization.ObjectMapperFactory.createObjectMapper().readTree(body);
            if (root != null && root.has("model")) {
                String model = root.path("model").asText();
                if (model != null && !model.isEmpty()) {
                    return model;
                }
            }
        } catch (Exception ignored) {
            // fail-soft: not parseable as JSON or no model field
        }
        return null;
    }
}
