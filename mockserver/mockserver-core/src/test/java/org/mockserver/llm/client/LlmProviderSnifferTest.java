package org.mockserver.llm.client;

import org.junit.After;
import org.junit.Test;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

public class LlmProviderSnifferTest {

    @After
    public void resetConfig() {
        ConfigurationProperties.llmProvider("");
        ConfigurationProperties.llmBaseUrl("");
    }

    // --- Well-known host detection ---

    @Test
    public void detectsOpenAiByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("api.openai.com"), is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void detectsOpenAiCaseInsensitive() {
        assertThat(LlmProviderSniffer.sniffByHost("API.OPENAI.COM"), is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void detectsOpenAiChatCompletionsPath() {
        // api.openai.com with /v1/chat/completions → OPENAI (not OPENAI_RESPONSES)
        assertThat(LlmProviderSniffer.sniffByHostAndPath("api.openai.com", "/v1/chat/completions"),
            is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void detectsOpenAiResponsesApiByPath() {
        // api.openai.com with /v1/responses → OPENAI_RESPONSES
        assertThat(LlmProviderSniffer.sniffByHostAndPath("api.openai.com", "/v1/responses"),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void detectsOpenAiResponsesApiWithTrailingId() {
        // api.openai.com with /v1/responses/resp_abc123 → OPENAI_RESPONSES
        assertThat(LlmProviderSniffer.sniffByHostAndPath("api.openai.com", "/v1/responses/resp_abc123"),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void detectsOpenAiCodexBackendResponsesByHostAndPath() {
        // opencode CLI hits the OpenAI Codex backend: chatgpt.com/backend-api/codex/responses,
        // which serves the standard Responses wire format → OPENAI_RESPONSES.
        assertThat(LlmProviderSniffer.sniffByHostAndPath("chatgpt.com", "/backend-api/codex/responses"),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void doesNotClassifyNonLlmChatgptComPath() {
        // chatgpt.com is path-gated: non-LLM traffic (oauth, account, etc.) must NOT be
        // classified as LLM traffic.
        assertThat(LlmProviderSniffer.sniffByHostAndPath("chatgpt.com", "/oauth/token"),
            is(Optional.empty()));
    }

    @Test
    public void detectsAzureOpenAiByWildcardHost() {
        assertThat(LlmProviderSniffer.sniffByHost("my-deployment.openai.azure.com"), is(Optional.of(Provider.AZURE_OPENAI)));
    }

    @Test
    public void detectsAnthropicByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("api.anthropic.com"), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void detectsGeminiByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("generativelanguage.googleapis.com"), is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void detectsBedrockByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("bedrock-runtime.us-east-1.amazonaws.com"), is(Optional.of(Provider.BEDROCK)));
    }

    @Test
    public void detectsBedrockAlternateRegion() {
        assertThat(LlmProviderSniffer.sniffByHost("bedrock.eu-west-1.amazonaws.com"), is(Optional.of(Provider.BEDROCK)));
    }

    // --- Ollama via configured base URL ---

    @Test
    public void detectsOllamaViaConfiguredBaseUrl() {
        ConfigurationProperties.llmBaseUrl("http://localhost:11434");
        assertThat(LlmProviderSniffer.sniffByHost("localhost"), is(Optional.of(Provider.OLLAMA)));
    }

    @Test
    public void detectsOllamaViaConfiguredBaseUrlWithPath() {
        ConfigurationProperties.llmBaseUrl("http://my-ollama-host:11434/v1");
        assertThat(LlmProviderSniffer.sniffByHost("my-ollama-host"), is(Optional.of(Provider.OLLAMA)));
    }

    // --- Fallback to configured provider (path-gated) ---

    @Test
    public void fallsBackToConfiguredProviderWithLlmPath() {
        ConfigurationProperties.llmProvider("ANTHROPIC");
        assertThat(LlmProviderSniffer.sniffByHostAndPath("unknown-host.example.com", "/v1/chat/completions"),
            is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void fallsBackToConfiguredProviderCaseInsensitiveWithLlmPath() {
        ConfigurationProperties.llmProvider("openai");
        assertThat(LlmProviderSniffer.sniffByHostAndPath("unknown-host.example.com", "/v1/messages"),
            is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void configuredProviderFallbackRequiresLlmPath() {
        // COR-05: configured provider should NOT match when path is not LLM-like
        ConfigurationProperties.llmProvider("ANTHROPIC");
        assertThat(LlmProviderSniffer.sniffByHostAndPath("unknown-host.example.com", "/api/users"),
            is(Optional.empty()));
    }

    @Test
    public void configuredProviderFallbackWithNullPath() {
        // COR-05: configured provider should NOT match when path is null
        ConfigurationProperties.llmProvider("OPENAI");
        assertThat(LlmProviderSniffer.sniffByHost("unknown-host.example.com"), is(Optional.empty()));
    }

    @Test
    public void configuredProviderFallbackMatchesVariousLlmPaths() {
        ConfigurationProperties.llmProvider("OPENAI");
        // /completions
        assertThat(LlmProviderSniffer.sniffByHostAndPath("custom-llm.example.com", "/v1/completions"),
            is(Optional.of(Provider.OPENAI)));
        // /embeddings
        assertThat(LlmProviderSniffer.sniffByHostAndPath("custom-llm.example.com", "/v1/embeddings"),
            is(Optional.of(Provider.OPENAI)));
        // :generatecontent (Gemini-style)
        assertThat(LlmProviderSniffer.sniffByHostAndPath("custom-llm.example.com", "/v1/models/gemini:generateContent"),
            is(Optional.of(Provider.OPENAI)));
        // /api/generate (Ollama-style)
        assertThat(LlmProviderSniffer.sniffByHostAndPath("custom-llm.example.com", "/api/generate"),
            is(Optional.of(Provider.OPENAI)));
        // /api/chat (Ollama-style)
        assertThat(LlmProviderSniffer.sniffByHostAndPath("custom-llm.example.com", "/api/chat"),
            is(Optional.of(Provider.OPENAI)));
        // /responses
        assertThat(LlmProviderSniffer.sniffByHostAndPath("custom-llm.example.com", "/v1/responses"),
            is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void returnsEmptyWhenNoProviderConfigured() {
        assertThat(LlmProviderSniffer.sniffByHostAndPath("unknown-host.example.com", "/v1/chat/completions"),
            is(Optional.empty()));
    }

    @Test
    public void returnsEmptyForNullHost() {
        assertThat(LlmProviderSniffer.sniffByHost(null), is(Optional.empty()));
    }

    @Test
    public void returnsEmptyForEmptyHost() {
        assertThat(LlmProviderSniffer.sniffByHost(""), is(Optional.empty()));
    }

    @Test
    public void returnsEmptyForInvalidConfiguredProvider() {
        ConfigurationProperties.llmProvider("NOT_A_REAL_PROVIDER");
        assertThat(LlmProviderSniffer.sniffByHostAndPath("unknown-host.example.com", "/v1/chat/completions"),
            is(Optional.empty()));
    }

    // --- sniff(HttpRequest) with socket address ---

    @Test
    public void sniffsProviderFromRequestSocketAddress() {
        HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/chat/completions")
            .withSocketAddress(true, "api.openai.com", 443);
        assertThat(LlmProviderSniffer.sniff(request), is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void sniffsOpenAiResponsesApiFromRequest() {
        HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/responses")
            .withSocketAddress(true, "api.openai.com", 443);
        assertThat(LlmProviderSniffer.sniff(request), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void sniffsProviderFromRequestHostHeader() {
        HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/messages")
            .withHeader("Host", "api.anthropic.com");
        assertThat(LlmProviderSniffer.sniff(request), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void sniffsProviderFromHostHeaderWithPort() {
        HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/messages")
            .withHeader("Host", "api.anthropic.com:443");
        assertThat(LlmProviderSniffer.sniff(request), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void returnsEmptyForNullRequest() {
        assertThat(LlmProviderSniffer.sniff(null), is(Optional.empty()));
    }

    @Test
    public void sniffReturnsEmptyForUnknownHostAndNonLlmPathWithConfiguredProvider() {
        // COR-05: configured provider + unknown host + non-LLM path → empty
        ConfigurationProperties.llmProvider("OPENAI");
        HttpRequest request = request()
            .withMethod("GET")
            .withPath("/api/users")
            .withSocketAddress(true, "example.com", 443);
        assertThat(LlmProviderSniffer.sniff(request), is(Optional.empty()));
    }

    @Test
    public void sniffReturnsProviderForUnknownHostAndLlmPathWithConfiguredProvider() {
        // COR-05: configured provider + unknown host + LLM path → configured provider
        ConfigurationProperties.llmProvider("ANTHROPIC");
        HttpRequest request = request()
            .withMethod("POST")
            .withPath("/v1/chat/completions")
            .withSocketAddress(true, "my-custom-llm.internal", 443);
        assertThat(LlmProviderSniffer.sniff(request), is(Optional.of(Provider.ANTHROPIC)));
    }

    // --- Model extraction ---

    @Test
    public void extractsModelFromOpenAiResponse() {
        org.mockserver.model.HttpResponse response = org.mockserver.model.HttpResponse.response()
            .withBody("{\"id\":\"chatcmpl-123\",\"model\":\"gpt-4o\",\"choices\":[{\"message\":{\"content\":\"hello\"}}]}");
        assertThat(LlmProviderSniffer.extractModelFromResponse(response), is("gpt-4o"));
    }

    @Test
    public void extractsModelFromAnthropicResponse() {
        org.mockserver.model.HttpResponse response = org.mockserver.model.HttpResponse.response()
            .withBody("{\"model\":\"claude-3-5-sonnet-20241022\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}");
        assertThat(LlmProviderSniffer.extractModelFromResponse(response), is("claude-3-5-sonnet-20241022"));
    }

    @Test
    public void returnsNullForNonJsonResponse() {
        org.mockserver.model.HttpResponse response = org.mockserver.model.HttpResponse.response()
            .withBody("not json");
        assertThat(LlmProviderSniffer.extractModelFromResponse(response) == null, is(true));
    }

    @Test
    public void returnsNullForResponseWithNoModel() {
        org.mockserver.model.HttpResponse response = org.mockserver.model.HttpResponse.response()
            .withBody("{\"data\":\"something\"}");
        assertThat(LlmProviderSniffer.extractModelFromResponse(response) == null, is(true));
    }

    @Test
    public void returnsNullForNullResponse() {
        assertThat(LlmProviderSniffer.extractModelFromResponse(null) == null, is(true));
    }

    @Test
    public void extractsModelFromRequestBody() {
        HttpRequest request = request()
            .withBody("{\"model\":\"gpt-4o-mini\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        assertThat(LlmProviderSniffer.extractModelFromRequest(request), is("gpt-4o-mini"));
    }

    @Test
    public void returnsNullForRequestWithNoModel() {
        HttpRequest request = request()
            .withBody("{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        assertThat(LlmProviderSniffer.extractModelFromRequest(request) == null, is(true));
    }

    // --- Offline analysis detection (detectForAnalysis / sniffByPath) ---
    // Recognises MOCKED LLM traffic on localhost that host-based sniff() misses,
    // so the optimisation report matches the Sessions view.

    @Test
    public void sniffByPathDetectsAnthropicMessages() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1/messages")), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void sniffByPathDetectsOpenAiChatCompletions() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1/chat/completions")), is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void sniffByPathDetectsOpenAiResponsesBeforeChatCompletions() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1/responses")), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void sniffByPathDetectsOpenAiCodexResponsesBackend() {
        // opencode's Codex backend path (/backend-api/codex/responses) matches the
        // OpenAI Responses path shape → OPENAI_RESPONSES, even without a known host.
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/backend-api/codex/responses")),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void detectForAnalysisRecognisesCodexBackendByHostAndPath() {
        HttpRequest req = request().withHeader("Host", "chatgpt.com").withPath("/backend-api/codex/responses");
        assertThat(LlmProviderSniffer.detectForAnalysis(req), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void sniffByPathDetectsAzureBeforeGenericOpenAi() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/openai/deployments/gpt4o/chat/completions")),
            is(Optional.of(Provider.AZURE_OPENAI)));
    }

    @Test
    public void sniffByPathDetectsBedrockAnthropic() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/model/anthropic.claude-3/invoke")),
            is(Optional.of(Provider.BEDROCK)));
    }

    @Test
    public void sniffByPathDetectsGemini() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1beta/models/gemini-2.0-flash:generateContent")),
            is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void sniffByPathDetectsOllamaChat() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/api/chat")), is(Optional.of(Provider.OLLAMA)));
    }

    @Test
    public void sniffByPathReturnsEmptyForNonLlmPath() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/api/chatbot")), is(Optional.empty()));
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/health")), is(Optional.empty()));
    }

    @Test
    public void detectForAnalysisRecognisesMockedLocalhostTraffic() {
        // No provider configured, host is localhost (mocked LLM response). sniff() returns
        // empty; detectForAnalysis falls back to the path shape.
        HttpRequest mocked = request().withHeader("Host", "localhost:1080").withPath("/v1/messages");
        assertThat(LlmProviderSniffer.sniff(mocked), is(Optional.empty()));
        assertThat(LlmProviderSniffer.detectForAnalysis(mocked), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void detectForAnalysisPrefersHostWhenKnown() {
        HttpRequest req = request().withHeader("Host", "api.openai.com").withPath("/v1/chat/completions");
        assertThat(LlmProviderSniffer.detectForAnalysis(req), is(Optional.of(Provider.OPENAI)));
    }

    // --- Body-shape detection (sniffByBodyShape / detectForAnalysis 2-arg) ---
    // Resilience guarantee: when the host AND path are unknown/non-LLM-looking, the
    // request/response wire format alone must still identify the provider, so capture
    // survives an LLM API or coding-CLI harness changing its host/path.

    @Test
    public void detectForAnalysisRecognisesOpenAiResponsesByBodyOnUnknownHostAndPath() {
        // Unknown private gateway, non-LLM-looking path: only the body identifies it.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"gpt-x\",\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"hi\"}]}]}");
        org.mockserver.model.HttpResponse res = org.mockserver.model.HttpResponse.response()
            .withBody("event: response.completed\ndata: {\"type\":\"response.completed\"}\n\n");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, res), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void detectForAnalysisRecognisesOpenAiChatByResponseBodyOnUnknownHostAndPath() {
        // Streaming Chat Completions chunk on an unknown host/path → OPENAI.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"gpt-x\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        org.mockserver.model.HttpResponse res = org.mockserver.model.HttpResponse.response()
            .withBody("data: {\"object\":\"chat.completion.chunk\",\"choices\":[]}\n\n");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, res), is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void detectForAnalysisRecognisesAnthropicByVersionHeaderOnUnknownHostAndPath() {
        // The required anthropic-version header identifies Anthropic regardless of host/path.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withHeader("anthropic-version", "2023-06-01")
            .withBody("{\"model\":\"claude-x\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, null), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void detectForAnalysisRecognisesAnthropicByResponseBodyOnUnknownHostAndPath() {
        // Non-streamed Anthropic message envelope (type + stop_reason) → ANTHROPIC.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"claude-x\"}");
        org.mockserver.model.HttpResponse res = org.mockserver.model.HttpResponse.response()
            .withBody("{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"stop_reason\":\"end_turn\"}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, res), is(Optional.of(Provider.ANTHROPIC)));
    }

    @Test
    public void detectForAnalysisRecognisesGeminiByRequestBodyOnUnknownHostAndPath() {
        // Gemini's top-level "contents" + "parts" identifies it without host/path.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"contents\":[{\"parts\":[{\"text\":\"hi\"}]}]}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, null), is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void sniffByBodyShapeReturnsEmptyForNonLlmTraffic() {
        // NEGATIVE: plain non-LLM JSON must not be mis-classified (no false positives).
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"user\":\"bob\",\"action\":\"login\"}");
        org.mockserver.model.HttpResponse res = org.mockserver.model.HttpResponse.response()
            .withBody("{\"status\":\"ok\"}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, res), is(Optional.empty()));
        assertThat(LlmProviderSniffer.detectForAnalysis(req, res), is(Optional.empty()));
    }

    @Test
    public void detectForAnalysisPrefersHostOverBodyWhenHostKnown() {
        // Regression: a known host wins — body shape is not even needed.
        HttpRequest req = request()
            .withHeader("Host", "api.anthropic.com")
            .withPath("/v1/messages")
            .withBody("{\"model\":\"claude-x\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, null), is(Optional.of(Provider.ANTHROPIC)));
    }

    // --- Embeddings / moderations must NOT be mis-classified as OpenAI Responses ---
    // A STRING-valued "input" is embeddings/moderations; only an "input" ARRAY (or a
    // Responses-distinctive field) is the Responses API.

    @Test
    public void embeddingsRequestIsNotClassifiedAsResponses() {
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v1/embeddings")
            .withBody("{\"model\":\"text-embedding-3-small\",\"input\":\"hi\"}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, null), is(Optional.empty()));
        assertThat(LlmProviderSniffer.detectForAnalysis(req, null), is(Optional.empty()));
    }

    @Test
    public void moderationsRequestIsNotClassifiedAsResponses() {
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v1/moderations")
            .withBody("{\"model\":\"omni-moderation-latest\",\"input\":\"some text to score\"}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, null), is(Optional.empty()));
    }

    @Test
    public void responsesRequestWithInputArrayIsClassifiedFromRequestBodyAlone() {
        // No response body — the "input" ARRAY in the request still identifies Responses.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"gpt-x\",\"input\":[{\"role\":\"user\",\"content\":[{\"type\":\"input_text\",\"text\":\"hi\"}]}]}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, null), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void responsesRequestWithPreviousResponseIdIsClassified() {
        // Responses-distinctive field identifies it even with a string-valued input.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"gpt-x\",\"input\":\"hi\",\"previous_response_id\":\"resp_123\"}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, null), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void responsesRequestWithInstructionsIsClassified() {
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"gpt-x\",\"input\":\"hi\",\"instructions\":\"be terse\"}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, null), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void chatCompletionsRequiresMessagesArrayNotBareSubstring() {
        // A string-valued "messages" must NOT be classified as OpenAI Chat Completions.
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/agent/run")
            .withBody("{\"model\":\"gpt-x\",\"messages\":\"not an array\"}");
        assertThat(LlmProviderSniffer.sniffByBodyShape(req, null), is(Optional.empty()));
    }

    // --- Cohere / Voyage / Vertex host detection ---

    @Test
    public void detectsCohereByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("api.cohere.com"), is(Optional.of(Provider.COHERE)));
    }

    @Test
    public void detectsVoyageByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("api.voyageai.com"), is(Optional.of(Provider.VOYAGE)));
    }

    @Test
    public void detectsVertexGeminiByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("us-central1-aiplatform.googleapis.com"),
            is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void detectsGlobalVertexGeminiByHost() {
        assertThat(LlmProviderSniffer.sniffByHost("aiplatform.googleapis.com"),
            is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void doesNotDetectSpoofedHostGluedBeforeVertexDomain() {
        // Security: an arbitrary host must not be prefixed onto the Vertex domain
        // and be mis-detected as gemini (incomplete URL substring sanitization).
        assertThat(LlmProviderSniffer.sniffByHost("evil.com-aiplatform.googleapis.com"),
            is(Optional.empty()));
    }

    @Test
    public void voyageHostDisambiguatesSharedRerankPath() {
        // Both Cohere and Voyage use /v1/rerank; the host decides which one.
        assertThat(LlmProviderSniffer.sniffByHostAndPath("api.voyageai.com", "/v1/rerank"),
            is(Optional.of(Provider.VOYAGE)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("api.cohere.com", "/v1/rerank"),
            is(Optional.of(Provider.COHERE)));
    }

    // --- Cohere / Vertex / Bedrock-Converse path detection (host unknown) ---

    @Test
    public void sniffByPathDetectsCohereRerank() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1/rerank")), is(Optional.of(Provider.COHERE)));
    }

    @Test
    public void sniffByPathDetectsCohereV2Rerank() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v2/rerank")), is(Optional.of(Provider.COHERE)));
    }

    @Test
    public void detectForAnalysisRecognisesCohereV2RerankByPathWhenTheBodiesDoNotShowIt() {
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/rerank")
            .withBody("{\"model\":\"rerank-v3.5\"}");
        org.mockserver.model.HttpResponse res = org.mockserver.model.HttpResponse.response()
            .withStatusCode(429)
            .withBody("{\"message\":\"too many requests\"}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, res), is(Optional.of(Provider.COHERE)));
    }

    @Test
    public void sniffByPathDetectsCohereChat() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1/chat")), is(Optional.of(Provider.COHERE)));
    }

    @Test
    public void sniffByPathKeepsChatCompletionsAsOpenAi() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/v1/chat/completions")),
            is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void sniffByPathDetectsVertexGemini() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/publishers/google/models/gemini-1.5-pro:generateContent")),
            is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void sniffByPathDetectsBedrockConverseForNonAnthropicModel() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/model/cohere.command-r-v1:0/converse")),
            is(Optional.of(Provider.BEDROCK)));
    }

    @Test
    public void sniffByPathDetectsBedrockInvokeWithResponseStream() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/model/anthropic.claude-3/invoke-with-response-stream")),
            is(Optional.of(Provider.BEDROCK)));
    }

    // --- case-insensitive path matching ---

    @Test
    public void sniffByPathIsCaseInsensitive() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/V1/MESSAGES")), is(Optional.of(Provider.ANTHROPIC)));
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/V1/RESPONSES")), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void sniffByPathDoesNotMatchCodexResponsesWithoutSegmentBoundary() {
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/mycodex/responses")), is(Optional.empty()));
    }

    // --- Cohere rerank body-shape detection ---

    @Test
    public void detectForAnalysisRecognisesCohereRerankByRequestBody() {
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/score")
            .withBody("{\"query\":\"what is mockserver\",\"documents\":[\"a mock server\",\"a real server\"]}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, null), is(Optional.of(Provider.COHERE)));
    }

    @Test
    public void detectForAnalysisRecognisesCohereRerankByResponseBody() {
        HttpRequest req = request()
            .withHeader("Host", "gateway.internal.example")
            .withPath("/v2/score")
            .withBody("{}");
        org.mockserver.model.HttpResponse res = org.mockserver.model.HttpResponse.response()
            .withBody("{\"results\":[{\"index\":0,\"relevance_score\":0.97},{\"index\":1,\"relevance_score\":0.12}]}");
        assertThat(LlmProviderSniffer.detectForAnalysis(req, res), is(Optional.of(Provider.COHERE)));
    }

    // --- configured-provider fallback: Gemini streaming and Bedrock paths ---

    @Test
    public void fallsBackToConfiguredProviderForGeminiStreamingOnAGateway() {
        ConfigurationProperties.llmProvider("GEMINI");
        assertThat(LlmProviderSniffer.sniffByHostAndPath("llm-gateway.internal",
                "/v1beta/models/gemini-2.5-flash:streamGenerateContent"),
            is(Optional.of(Provider.GEMINI)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("llm-gateway.internal",
                "/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse"),
            is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void fallsBackToConfiguredProviderForEveryBedrockRuntimePath() {
        ConfigurationProperties.llmProvider("BEDROCK");
        for (String operation : new String[]{"converse", "converse-stream", "invoke", "invoke-with-response-stream"}) {
            assertThat(operation, LlmProviderSniffer.sniffByHostAndPath("vpce-0abc.bedrock-runtime.vpce.example.internal",
                    "/model/anthropic.claude-sonnet-4-5-20250929-v1%3A0/" + operation),
                is(Optional.of(Provider.BEDROCK)));
        }
    }

    @Test
    public void doesNotFallBackForANonLlmPathThatOnlyMentionsAModel() {
        ConfigurationProperties.llmProvider("BEDROCK");
        assertThat(LlmProviderSniffer.sniffByHostAndPath("internal.example.com", "/model/123/details"),
            is(Optional.empty()));
    }

    // --- accountable endpoints: completions, embeddings and rerank ---

    private static void assertOperation(String method, String path, LlmProviderSniffer.Operation expected) {
        assertOperation(Provider.OPENAI, method, path, expected);
    }

    private static void assertOperation(Provider provider, String method, String path, LlmProviderSniffer.Operation expected) {
        assertThat(provider + " " + method + " " + path, LlmProviderSniffer.accountableOperation(provider, method, path), is(Optional.ofNullable(expected)));
    }

    @Test
    public void accountsEachProvidersCompletionEndpointsAsChat() {
        String[] completionPaths = {
            // OpenAI
            "/v1/chat/completions", "/v1/completions", "/v1/responses",
            // OpenAI Responses compaction runs the model and reports usage
            "/v1/responses/compact",
            // Azure OpenAI
            "/openai/deployments/gpt-4o/chat/completions?api-version=2024-10-21", "/openai/v1/responses",
            "/openai/responses?api-version=2025-04-01-preview",
            // OpenAI Codex backend
            "/backend-api/codex/responses",
            // Mistral
            "/v1/fim/completions", "/v1/agents/completions",
            // OpenRouter, Groq, DeepSeek
            "/api/v1/chat/completions", "/openai/v1/chat/completions", "/chat/completions",
            // Anthropic
            "/v1/messages",
            // Gemini and Vertex AI
            "/v1beta/models/gemini-2.5-flash:generateContent", "/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse",
            "/v1/projects/p/locations/us-central1/publishers/google/models/gemini-2.5-flash:generateContent",
            // Bedrock
            "/model/anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse",
            "/model/anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse-stream",
            "/model/amazon.nova-pro-v1%3A0/invoke", "/model/amazon.nova-pro-v1%3A0/invoke-with-response-stream",
            // Ollama
            "/api/chat", "/api/generate",
            // Cohere
            "/v1/chat", "/v2/chat",
            // Claude on Vertex AI
            "/v1/projects/p/locations/us-east5/publishers/anthropic/models/claude-sonnet-4-5@20250929:rawPredict",
            "/v1/projects/p/locations/us-east5/publishers/anthropic/models/claude-sonnet-4-5@20250929:streamRawPredict",
        };
        for (String path : completionPaths) {
            assertOperation("POST", path, LlmProviderSniffer.Operation.CHAT);
        }
    }

    @Test
    public void accountsEachProvidersEmbeddingsEndpointsAsEmbeddings() {
        String[] embeddingsPaths = {
            // OpenAI, Mistral, OpenRouter, Voyage
            "/v1/embeddings", "/api/v1/embeddings",
            // Azure OpenAI
            "/openai/deployments/text-embedding-3-small/embeddings?api-version=2024-10-21",
            // Gemini
            "/v1beta/models/gemini-embedding-001:embedContent", "/v1beta/models/gemini-embedding-001:batchEmbedContents",
            // Ollama, current and legacy
            "/api/embed", "/api/embeddings",
            // Cohere
            "/v1/embed", "/v2/embed",
            // Voyage
            "/v1/multimodalembeddings", "/v1/contextualizedembeddings",
            // Bedrock embedding models, invoked on the chat models' path
            "/model/amazon.titan-embed-text-v2%3A0/invoke", "/model/cohere.embed-v4%3A0/invoke",
            // Vertex AI embedding models on the publisher-model predict method
            "/v1/projects/p/locations/us-central1/publishers/google/models/text-embedding-005:predict",
            "/v1/projects/p/locations/us-central1/publishers/google/models/gemini-embedding-001:predict",
            "/v1/projects/p/locations/us-central1/publishers/google/models/textembedding-gecko@003:predict",
        };
        for (String path : embeddingsPaths) {
            assertOperation("POST", path, LlmProviderSniffer.Operation.EMBEDDINGS);
        }
    }

    @Test
    public void accountsEachProvidersRerankEndpointsAsRerank() {
        String[] rerankPaths = {
            // Cohere and Voyage
            "/v1/rerank", "/v2/rerank",
            // Bedrock Agent Runtime, and a Bedrock rerank model invoked on the chat models' path
            "/rerank", "/model/cohere.rerank-v3-5%3A0/invoke",
        };
        for (String path : rerankPaths) {
            assertOperation("POST", path, LlmProviderSniffer.Operation.RERANK);
        }
    }

    @Test
    public void doesNotAccountOtherEndpointsOfAnLlmHost() {
        String[] otherPaths = {
            // Anthropic token count and batches
            "/v1/messages/count_tokens", "/v1/messages/batches",
            // OpenAI Responses token count and cancel, a stored chat completion's update, Assistants thread messages
            "/v1/responses/input_tokens", "/v1/responses/resp_123/cancel", "/v1/chat/completions/chatcmpl-123",
            "/v1/threads/thread_abc/messages",
            // Ollama model management
            "/api/pull", "/api/push", "/api/create", "/api/show",
            // other OpenAI endpoints
            "/v1/realtime/events", "/v1/moderations", "/v1/files", "/v1/models",
            // Gemini token count, Cohere's other endpoints, Bedrock's other paths
            "/v1beta/models/gemini-2.5-flash:countTokens", "/v2/chatbots", "/v1/tokenize", "/model/123/details",
            // Claude on Vertex AI token count
            "/v1/projects/p/locations/us-east5/publishers/anthropic/models/count-tokens:rawPredict",
            // OpenAI Conversations API objects, which do not call a model
            "/v1/conversations", "/v1/conversations/conv_123",
            // Vertex AI predict on a model that is not an embedding model, or on a deployed endpoint
            "/v1/projects/p/locations/us-central1/publishers/google/models/imagen-3.0-generate-002:predict",
            "/v1/projects/p/locations/us-central1/endpoints/1234567890:predict",
            "",
        };
        for (String path : otherPaths) {
            assertOperation("POST", path, null);
        }
        assertOperation("POST", null, null);
    }

    @Test
    public void accountsOnlyAPostBecauseEveryOtherMethodReadsOrDeletesWithoutCallingAModel() {
        assertOperation("GET", "/v1/models", null);
        assertOperation("GET", "/v1/chat/completions", null);
        assertOperation("GET", "/v1/chat/completions/chatcmpl-123", null);
        assertOperation("GET", "/v1/responses/resp_123", null);
        assertOperation("GET", "/v1/responses/resp_123?stream=true", null);
        assertOperation("DELETE", "/v1/responses/resp_123", null);
        assertOperation("GET", "/v1/messages/batches/msgbatch_01/results", null);
        assertOperation("GET", "/api/tags", null);
        assertOperation("OPTIONS", "/v1/chat/completions", null);
        assertOperation("HEAD", "/v1/embeddings", null);

        assertOperation("post", "/v1/chat/completions", LlmProviderSniffer.Operation.CHAT);
        // a request whose method is not known is judged by its path alone
        assertOperation(null, "/v1/chat/completions", LlmProviderSniffer.Operation.CHAT);
        assertOperation("", "/v1/embeddings", LlmProviderSniffer.Operation.EMBEDDINGS);
    }

    @Test
    public void accountsMistralConversationsOnlyOnTheMistralHost() {
        String[] conversationPaths = {"/v1/conversations", "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2",
            "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2/restart"};
        for (String path : conversationPaths) {
            assertOperation(Provider.MISTRAL, "POST", path, LlmProviderSniffer.Operation.CHAT);
            assertOperation(Provider.OPENAI, "POST", path, null);
        }
        assertOperation(Provider.MISTRAL, "GET", "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2", null);
        assertOperation(Provider.MISTRAL, "GET", "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2/history", null);
        assertOperation(Provider.MISTRAL, "GET", "/v1/conversations/conv_06835b9dc0c7749180001ed2adbbc4e2/messages", null);
    }

    @Test
    public void recognisesAGetOfABackgroundResponseOrADeferredCompletionAsAPolledCompletion() {
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI_RESPONSES, "GET", "/v1/responses/resp_123"), is(true));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI_RESPONSES, "GET", "/openai/v1/responses/resp_123?api-version=preview"), is(true));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.XAI, "GET", "/v1/chat/deferred-completion/f15c114e-f47d-40ca-8d5c-8c23d656eeb6"), is(true));
        assertOperation(Provider.OPENAI_RESPONSES, "GET", "/v1/responses/resp_123", LlmProviderSniffer.Operation.CHAT);
        assertOperation(Provider.XAI, "GET", "/v1/chat/deferred-completion/f15c114e-f47d-40ca-8d5c-8c23d656eeb6", LlmProviderSniffer.Operation.CHAT);

        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI_RESPONSES, "GET", "/v1/responses/resp_123/input_items"), is(false));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI_RESPONSES, "DELETE", "/v1/responses/resp_123"), is(false));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI_RESPONSES, "POST", "/v1/responses/resp_123"), is(false));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI, "GET", "/v1/chat/completions/chatcmpl-123"), is(false));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.OPENAI, "GET", "/v1/chat/deferred-completion/abc"), is(false));
        assertThat(LlmProviderSniffer.isPolledCompletion(Provider.XAI, "GET", "/v1/responses/resp_123"), is(false));
    }

    @Test
    public void countsAPolledResponseOnlyWhenItRanInTheBackgroundAndHasFinished() {
        String finished = "{\"id\":\"resp_123\",\"object\":\"response\",\"background\":true,\"status\":\"completed\","
            + "\"usage\":{\"input_tokens\":10,\"output_tokens\":20,\"total_tokens\":30}}";
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.OPENAI_RESPONSES, finished), is("resp_123"));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.OPENAI_RESPONSES, finished.replace("completed", "incomplete")), is("resp_123"));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.OPENAI_RESPONSES, finished.replace("completed", "in_progress")), is((String) null));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.OPENAI_RESPONSES, finished.replace("\"background\":true", "\"background\":false")), is((String) null));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.OPENAI_RESPONSES,
            "{\"id\":\"resp_123\",\"object\":\"response\",\"background\":true,\"status\":\"queued\",\"usage\":null}"), is((String) null));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.XAI,
            "{\"id\":\"a3d1008e-4544-40d4-d075-11527e794e4a\",\"object\":\"chat.completion\",\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":5}}"),
            is("a3d1008e-4544-40d4-d075-11527e794e4a"));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.XAI, ""), is((String) null));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.XAI, "not json"), is((String) null));
        assertThat(LlmProviderSniffer.polledCompletionId(Provider.OPENAI, finished), is((String) null));
    }

    @Test
    public void detectsClaudeOnVertexAiAsAnthropic() {
        assertThat(LlmProviderSniffer.sniffByHostAndPath("us-east5-aiplatform.googleapis.com",
            "/v1/projects/p/locations/us-east5/publishers/anthropic/models/claude-sonnet-4-5@20250929:rawPredict"), is(Optional.of(Provider.ANTHROPIC)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("us-east5-aiplatform.googleapis.com",
            "/v1/projects/p/locations/us-east5/publishers/google/models/gemini-2.5-flash:generateContent"), is(Optional.of(Provider.GEMINI)));
    }

    @Test
    public void detectsMistralOnVertexAiAsMistral() {
        // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/partner-models/mistral
        for (String method : new String[]{"rawPredict", "streamRawPredict"}) {
            String path = "/v1/projects/p/locations/us-central1/publishers/mistralai/models/mistral-medium-3:" + method;
            assertThat(path, LlmProviderSniffer.sniffByHostAndPath("us-central1-aiplatform.googleapis.com", path), is(Optional.of(Provider.MISTRAL)));
            assertOperation(Provider.MISTRAL, "POST", path, LlmProviderSniffer.Operation.CHAT);
        }
        assertThat(LlmProviderSniffer.sniffByHostAndPath("aiplatform.googleapis.com",
            "/v1/projects/p/locations/global/publishers/google/models/text-embedding-005:predict"), is(Optional.of(Provider.GEMINI)));
    }

    // --- Bedrock Agent Runtime rerank model from the request body ---

    @Test
    public void readsTheModelABedrockAgentRuntimeRerankRequestNamesInItsBody() {
        // https://docs.aws.amazon.com/bedrock/latest/APIReference/API_agent-runtime_Rerank.html
        String body = "{\"queries\":[{\"type\":\"TEXT\",\"textQuery\":{\"text\":\"What is the capital of France?\"}}],"
            + "\"rerankingConfiguration\":{\"type\":\"BEDROCK_RERANKING_MODEL\",\"bedrockRerankingConfiguration\":{"
            + "\"numberOfResults\":2,\"modelConfiguration\":{\"modelArn\":\"%s\"}}},"
            + "\"sources\":[{\"type\":\"INLINE\",\"inlineDocumentSource\":{\"type\":\"TEXT\",\"textDocument\":{\"text\":\"Paris\"}}}]}";
        assertThat(LlmProviderSniffer.extractBedrockRerankModelFromRequest(org.mockserver.model.HttpRequest.request("/rerank")
                .withBody(String.format(body, "arn:aws:bedrock:us-west-2::foundation-model/amazon.rerank-v1:0"))),
            is("amazon.rerank-v1:0"));
        assertThat(LlmProviderSniffer.extractBedrockRerankModelFromRequest(org.mockserver.model.HttpRequest.request("/rerank")
                .withBody(String.format(body, "arn:aws:bedrock:us-west-2::foundation-model/cohere.rerank-v3-5:0"))),
            is("cohere.rerank-v3-5:0"));
        assertThat(LlmProviderSniffer.extractBedrockRerankModelFromRequest(org.mockserver.model.HttpRequest.request("/rerank")
            .withBody("{\"queries\":[]}")), is((String) null));
        assertThat(LlmProviderSniffer.extractBedrockRerankModelFromRequest(org.mockserver.model.HttpRequest.request("/rerank")
            .withBody("not json")), is((String) null));
        assertThat(LlmProviderSniffer.extractBedrockRerankModelFromRequest(null), is((String) null));
    }

    @Test
    public void namesTheSemanticConventionOperationOfEachAccountedCall() {
        assertThat(LlmProviderSniffer.Operation.CHAT.genAiOperationName(), is("chat"));
        assertThat(LlmProviderSniffer.Operation.EMBEDDINGS.genAiOperationName(), is("embeddings"));
        assertThat(LlmProviderSniffer.Operation.RERANK.genAiOperationName(), is("rerank"));
    }

    @Test
    public void extractsTheModelAGeminiRequestNamesInItsPath() {
        assertThat(LlmProviderSniffer.extractGeminiModelFromPath("/v1beta/models/gemini-embedding-001:embedContent"), is("gemini-embedding-001"));
        assertThat(LlmProviderSniffer.extractGeminiModelFromPath("/v1beta/models/text-embedding-004:batchEmbedContents?key=abc"), is("text-embedding-004"));
        assertThat(LlmProviderSniffer.extractGeminiModelFromPath(
            "/v1/projects/p/locations/us-central1/publishers/google/models/gemini-2.5-flash:generateContent"), is("gemini-2.5-flash"));
        assertThat(LlmProviderSniffer.extractGeminiModelFromPath("/v1beta/models"), is((String) null));
        assertThat(LlmProviderSniffer.extractGeminiModelFromPath(null), is((String) null));
    }

    // --- Bedrock model id from the request path ---

    @Test
    public void readsTheBedrockModelIdFromEachInvocationPath() {
        for (String operation : new String[]{"converse", "converse-stream", "invoke", "invoke-with-response-stream"}) {
            assertThat(operation, LlmProviderSniffer.extractBedrockModelFromPath(
                    "/model/anthropic.claude-sonnet-4-5-20250929-v1%3A0/" + operation),
                is("anthropic.claude-sonnet-4-5-20250929-v1:0"));
        }
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/model/anthropic.claude-sonnet-4-5-20250929-v1:0/converse"),
            is("anthropic.claude-sonnet-4-5-20250929-v1:0"));
    }

    @Test
    public void mapsAnInferenceProfileOrArnToTheFoundationModelItNames() {
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/model/us.anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse"),
            is("anthropic.claude-sonnet-4-5-20250929-v1:0"));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/model/global.anthropic.claude-sonnet-4-5-20250929-v1%3A0/converse-stream"),
            is("anthropic.claude-sonnet-4-5-20250929-v1:0"));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/model/us-gov.anthropic.claude-sonnet-4-5-20250929-v1%3A0/invoke"),
            is("anthropic.claude-sonnet-4-5-20250929-v1:0"));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath(
                "/model/arn%3Aaws%3Abedrock%3Aus-east-1%3A%3Afoundation-model%2Famazon.nova-pro-v1%3A0/converse"),
            is("amazon.nova-pro-v1:0"));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath(
                "/model/arn%3Aaws%3Abedrock%3Aeu-west-1%3A123456789012%3Ainference-profile%2Feu.anthropic.claude-haiku-4-5-20251001-v1%3A0/converse"),
            is("anthropic.claude-haiku-4-5-20251001-v1:0"));
    }

    @Test
    public void keepsABedrockIdThatNamesNoFoundationModel() {
        String applicationProfile = "arn:aws:bedrock:us-east-1:123456789012:application-inference-profile/a1b2c3d4e5f6";
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath(
                "/model/arn%3Aaws%3Abedrock%3Aus-east-1%3A123456789012%3Aapplication-inference-profile%2Fa1b2c3d4e5f6/converse"),
            is(applicationProfile));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/model/amazon.titan-embed-text-v2%3A0/invoke"),
            is("amazon.titan-embed-text-v2:0"));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/v1/chat/completions"), is((String) null));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath("/model/x/details"), is((String) null));
        assertThat(LlmProviderSniffer.extractBedrockModelFromPath(null), is((String) null));
    }
}
