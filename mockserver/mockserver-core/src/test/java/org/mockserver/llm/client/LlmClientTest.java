package org.mockserver.llm.client;

import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;
import org.mockserver.serialization.ObjectMapperFactory;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpResponse.response;

/**
 * Offline build/parse tests for the runtime-LLM clients — no network: each test
 * builds a request from a fixed conversation and parses a canned provider
 * response.
 */
public class LlmClientTest {

    private static ParsedConversation conversation() {
        return ParsedConversation.of(Arrays.asList(
            new ParsedMessage(ParsedMessage.Role.SYSTEM, "You are helpful.", null, null),
            new ParsedMessage(ParsedMessage.Role.USER, "What is the capital of France?", null, null)
        ));
    }

    // --- Ollama (primary backend) ---

    @Test
    public void ollamaBuildsChatRequestWithDeterministicOptions() {
        HttpRequest request = new OllamaLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.OLLAMA, null), conversation());
        assertThat(request.getMethod().getValue(), is("POST"));
        assertThat(request.getPath().getValue(), is("/api/chat"));
        String body = request.getBodyAsString();
        assertThat(body, containsString("\"stream\":false"));
        assertThat(body, containsString("\"temperature\":0"));
        assertThat(body, containsString("\"seed\":0"));
        assertThat(body, containsString("capital of France"));
    }

    @Test
    public void ollamaParsesChatResponse() {
        Completion completion = new OllamaLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"message\":{\"role\":\"assistant\",\"content\":\"Paris.\"}," +
                "\"done_reason\":\"stop\",\"prompt_eval_count\":11,\"eval_count\":3}"));
        assertThat(completion.getText(), is("Paris."));
        assertThat(completion.getStopReason(), is("stop"));
        assertThat(completion.getUsage().getInputTokens(), is(11));
        assertThat(completion.getUsage().getOutputTokens(), is(3));
    }

    @Test
    public void ollamaHonoursBackendBaseUrl() {
        HttpRequest request = new OllamaLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.OLLAMA, "http://ollama.internal:9999", null, "mistral", null, null),
            conversation());
        assertThat(request.getFirstHeader("Host"), is("ollama.internal:9999"));
        assertThat(request.getBodyAsString(), containsString("\"model\":\"mistral\""));
    }

    // --- OpenAI ---

    @Test
    public void openAiBuildsBearerAuthAndChatBody() {
        HttpRequest request = new OpenAiLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.OPENAI, "sk-test"), conversation());
        assertThat(request.getPath().getValue(), is("/v1/chat/completions"));
        assertThat(request.getFirstHeader("Authorization"), is("Bearer sk-test"));
        assertThat(request.getFirstHeader("Host"), is("api.openai.com"));
    }

    @Test
    public void openAiParsesChatResponse() {
        Completion completion = new OpenAiLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"choices\":[{\"message\":{\"content\":\"Paris\"},\"finish_reason\":\"stop\"}]," +
                "\"usage\":{\"prompt_tokens\":9,\"completion_tokens\":1}}"));
        assertThat(completion.getText(), is("Paris"));
        assertThat(completion.getStopReason(), is("stop"));
        assertThat(completion.getUsage().getInputTokens(), is(9));
    }

    // --- Anthropic ---

    @Test
    public void anthropicHoistsSystemAndSetsVersionAndKey() {
        HttpRequest request = new AnthropicLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.ANTHROPIC, "ak-test"), conversation());
        assertThat(request.getPath().getValue(), is("/v1/messages"));
        assertThat(request.getFirstHeader("x-api-key"), is("ak-test"));
        assertThat(request.getFirstHeader("anthropic-version"), is(notNullValue()));
        String body = request.getBodyAsString();
        assertThat(body, containsString("\"system\":\"You are helpful.\""));
        // system message must NOT appear in the messages array
        assertThat(body, containsString("\"role\":\"user\""));
    }

    @Test
    public void anthropicParsesContentBlocks() {
        Completion completion = new AnthropicLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"content\":[{\"type\":\"text\",\"text\":\"Paris\"}],\"stop_reason\":\"end_turn\"," +
                "\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}"));
        assertThat(completion.getText(), is("Paris"));
        assertThat(completion.getStopReason(), is("end_turn"));
        assertThat(completion.getUsage().getOutputTokens(), is(2));
    }

    // --- Gemini ---

    @Test
    public void geminiPutsKeyInQueryAndModelInPath() {
        HttpRequest request = new GeminiLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.GEMINI, null, "gk-test", "gemini-1.5-pro", null, null),
            conversation());
        assertThat(request.getPath().getValue(), containsString("/v1beta/models/gemini-1.5-pro:generateContent"));
        assertThat(request.getFirstQueryStringParameter("key"), is("gk-test"));
    }

    @Test
    public void geminiParsesCandidates() {
        Completion completion = new GeminiLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Paris\"}]},\"finishReason\":\"STOP\"}]," +
                "\"usageMetadata\":{\"promptTokenCount\":8,\"candidatesTokenCount\":1}}"));
        assertThat(completion.getText(), is("Paris"));
        assertThat(completion.getStopReason(), is("STOP"));
        assertThat(completion.getUsage().getInputTokens(), is(8));
    }

    // --- OpenAI Responses ---

    @Test
    public void openAiResponsesBuildsBearerAuthAndInputBody() {
        HttpRequest request = new OpenAiResponsesLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.OPENAI_RESPONSES, "sk-resp"), conversation());
        assertThat(request.getPath().getValue(), is("/v1/responses"));
        assertThat(request.getFirstHeader("Authorization"), is("Bearer sk-resp"));
        assertThat(request.getBodyAsString(), containsString("\"input\""));
    }

    @Test
    public void openAiResponsesParsesOutputText() {
        Completion completion = new OpenAiResponsesLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"output_text\":\"Paris\",\"status\":\"completed\"," +
                "\"usage\":{\"input_tokens\":11,\"output_tokens\":2}}"));
        assertThat(completion.getText(), is("Paris"));
        assertThat(completion.getStopReason(), is("completed"));
        assertThat(completion.getUsage().getInputTokens(), is(11));
    }

    // --- Azure OpenAI ---

    @Test
    public void azureOpenAiUsesDeploymentPathAndApiKeyHeader() {
        HttpRequest request = new AzureOpenAiLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.AZURE_OPENAI, "https://my-resource.openai.azure.com", "az-key", null, null, null), conversation());
        assertThat(request.getFirstHeader("Host"), is("my-resource.openai.azure.com"));
        assertThat(request.getPath().getValue(), is("/openai/deployments/gpt-4o-mini/chat/completions"));
        assertThat(request.getFirstHeader("api-key"), is("az-key"));
        // Azure uses the api-key header, NOT Authorization: Bearer
        assertThat(request.getFirstHeader("Authorization"), is(""));
        assertThat(request.getFirstQueryStringParameter("api-version"), is(notNullValue()));
    }

    @Test
    public void azureOpenAiRefusesToBuildARequestWithoutABaseUrlSoTheKeyGoesToNoDefaultHost() {
        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> new AzureOpenAiLlmClient()
            .buildCompletionRequest(LlmBackend.of(Provider.AZURE_OPENAI, "az-key"), conversation()));
        assertThat(refused.getMessage(), containsString("baseUrl"));
        assertThat(refused.getMessage(), not(containsString("az-key")));
    }

    @Test
    public void azureOpenAiParsesChatResponseLikeOpenAi() {
        Completion completion = new AzureOpenAiLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"choices\":[{\"message\":{\"content\":\"Paris\"},\"finish_reason\":\"stop\"}]," +
                "\"usage\":{\"prompt_tokens\":7,\"completion_tokens\":1}}"));
        assertThat(completion.getText(), is("Paris"));
        assertThat(completion.getUsage().getInputTokens(), is(7));
    }

    // --- Bedrock (Anthropic-on-Bedrock) ---

    @Test
    public void bedrockUsesInvokePathWithAnthropicBody() {
        HttpRequest request = new BedrockLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.BEDROCK, null), conversation());
        assertThat(request.getPath().getValue(), containsString("/model/"));
        assertThat(request.getPath().getValue(), containsString("/invoke"));
        // Anthropic-shaped body: system hoisted out, a messages array remains
        String body = request.getBodyAsString();
        assertThat(body, containsString("\"messages\""));
        assertThat(body, containsString("\"system\":\"You are helpful.\""));
    }

    @Test
    public void bedrockParsesAnthropicShapedResponse() {
        Completion completion = new BedrockLlmClient().parseCompletionResponse(response()
            .withStatusCode(200)
            .withBody("{\"content\":[{\"type\":\"text\",\"text\":\"Paris\"}],\"stop_reason\":\"end_turn\"," +
                "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}"));
        assertThat(completion.getText(), is("Paris"));
        assertThat(completion.getUsage().getOutputTokens(), is(3));
    }

    // --- registry ---

    @Test
    public void registryHasAClientForEveryProvider() {
        for (Provider provider : Provider.values()) {
            assertThat("client for " + provider, LlmClientRegistry.getInstance().lookup(provider).isPresent(), is(true));
        }
    }

    @Test
    public void cohereAndVoyageClientsReadUsageButAreNotRuntimeBackends() {
        for (LlmClient client : new LlmClient[]{new CohereLlmClient(), new VoyageLlmClient()}) {
            try {
                client.buildCompletionRequest(LlmBackend.of(client.provider(), "key"), conversation());
                throw new AssertionError("expected " + client.provider() + " to build no request");
            } catch (UnsupportedOperationException expected) {
                assertThat(expected.getMessage(), containsString("not supported as a runtime LLM backend"));
            }
        }
    }

    // --- Cohere: ApiMeta / Usage with billed_units, tokens, cached_tokens ---
    // https://docs.cohere.com/reference/embed, /reference/rerank, /reference/chat and the cohere-python
    // types ApiMeta, Usage, UsageBilledUnits, UsageTokens

    @Test
    public void cohereReadsTheBilledInputTokensOfAnEmbedResponse() {
        Completion completion = new CohereLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"id\":\"da6e531f-54c6-4a73-bf92-f60566d8d753\",\"embeddings\":{\"float\":[[0.016296387,-0.008354187]]},"
                + "\"texts\":[\"hello\",\"goodbye\"],\"meta\":{\"api_version\":{\"version\":\"2\",\"is_experimental\":true},"
                + "\"billed_units\":{\"input_tokens\":2},\"warnings\":[\"You are using an experimental version\"]}}"));
        assertThat(completion.getUsage().getInputTokens(), is(2));
        assertThat(completion.getUsage().getOutputTokens(), is((Integer) null));
        assertThat(completion.getModel(), is((String) null));
    }

    @Test
    public void cohereReadsTheBilledTokensOfAV2ChatResponseInPreferenceToTheTokensUsed() {
        Completion completion = new CohereLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"id\":\"c14c80c3-18eb-4519-9460-6c92edd8cfb4\",\"finish_reason\":\"COMPLETE\",\"message\":{\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"LLMs stand for Large Language Models.\"}]},"
                + "\"usage\":{\"billed_units\":{\"input_tokens\":5,\"output_tokens\":418},"
                + "\"tokens\":{\"input_tokens\":71,\"output_tokens\":418},\"cached_tokens\":3}}"));
        assertThat(completion.getText(), is("LLMs stand for Large Language Models."));
        assertThat(completion.getStopReason(), is("COMPLETE"));
        assertThat(completion.getUsage().getInputTokens(), is(5));
        assertThat(completion.getUsage().getOutputTokens(), is(418));
        assertThat(completion.getUsage().getCachedInputTokens(), is(3));
    }

    @Test
    public void cohereReadsTheMetaOfAV1ChatResponseAndFallsBackToTheTokensUsed() {
        Completion completion = new CohereLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"text\":\"Paris.\",\"generation_id\":\"0b1fbc5b-0c3c-4b4e-8a4e-3d0a0b9c3e5f\",\"finish_reason\":\"COMPLETE\","
                + "\"meta\":{\"api_version\":{\"version\":\"1\"},\"tokens\":{\"input_tokens\":83,\"output_tokens\":2}}}"));
        assertThat(completion.getText(), is("Paris."));
        assertThat(completion.getUsage().getInputTokens(), is(83));
        assertThat(completion.getUsage().getOutputTokens(), is(2));
    }

    @Test
    public void cohereReportsNoTokensForARerankBilledInSearchUnits() {
        Completion completion = new CohereLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"results\":[{\"index\":3,\"relevance_score\":0.999071},{\"index\":0,\"relevance_score\":0.32713068}],"
                + "\"id\":\"07734bd2-2473-4f07-94e1-0d9f0e6843cf\",\"meta\":{\"api_version\":{\"version\":\"2\",\"is_experimental\":false},"
                + "\"billed_units\":{\"search_units\":1}}}"));
        assertThat(completion.getUsage(), is((org.mockserver.model.Usage) null));
    }

    // --- Voyage: usage.total_tokens ---
    // https://docs.voyageai.com/reference/embeddings-api, /reranker-api, /multimodal-embeddings-api

    @Test
    public void voyageReadsTheModelAndTotalTokensOfEmbeddingsRerankAndMultimodalResponses() {
        Completion embeddings = new VoyageLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[-0.01,0.02],\"index\":0}],"
                + "\"model\":\"voyage-3.5\",\"usage\":{\"total_tokens\":10}}"));
        assertThat(embeddings.getModel(), is("voyage-3.5"));
        assertThat(embeddings.getUsage().getInputTokens(), is(10));
        assertThat(embeddings.getUsage().getOutputTokens(), is((Integer) null));

        Completion rerank = new VoyageLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"object\":\"list\",\"data\":[{\"relevance_score\":0.4375,\"index\":0}],\"model\":\"rerank-2.5\","
                + "\"usage\":{\"total_tokens\":26}}"));
        assertThat(rerank.getModel(), is("rerank-2.5"));
        assertThat(rerank.getUsage().getInputTokens(), is(26));

        Completion multimodal = new VoyageLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"embedding\":[0.1],\"index\":0}],"
                + "\"model\":\"voyage-multimodal-3\",\"usage\":{\"text_tokens\":5,\"image_pixels\":2000000,\"total_tokens\":3576}}"));
        assertThat(multimodal.getUsage().getInputTokens(), is(3576));
    }

    // --- Bedrock InvokeModel bodies without usage ---

    @Test
    public void bedrockReadsTheInputTokenCountOfATitanEmbeddingsBody() {
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-embed-text.html
        Completion completion = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"embedding\":[0.1,0.2],\"inputTextTokenCount\":6,\"embeddingsByType\":{\"float\":[0.1,0.2]}}"));
        assertThat(completion.getUsage().getInputTokens(), is(6));
    }

    @Test
    public void bedrockReadsTheTokenCountHeadersWhenTheBodyHasNoUsage() {
        // the headers InvokeModel returns, read by langchain-aws (llms/bedrock.py) and litellm
        Completion completion = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withHeader("x-amzn-bedrock-input-token-count", "9")
            .withHeader("x-amzn-bedrock-output-token-count", "0")
            .withBody("{\"embeddings\":[[0.01,-0.02]],\"id\":\"6b2c4a6e-1e9f-4f3c-9d25-4c2a1f0e8b7d\","
                + "\"response_type\":\"embeddings_floats\",\"texts\":[\"hello world\"]}"));
        assertThat(completion.getUsage().getInputTokens(), is(9));
        assertThat(completion.getUsage().getOutputTokens(), is(0));

        Completion llama = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "12")
            .withHeader("X-Amzn-Bedrock-Output-Token-Count", "34")
            .withHeader("X-Amzn-Bedrock-Cache-Read-Input-Token-Count", "4")
            .withHeader("X-Amzn-Bedrock-Cache-Write-Input-Token-Count", "2")
            .withBody("{\"generation\":\"Paris\",\"prompt_token_count\":12,\"generation_token_count\":34,\"stop_reason\":\"stop\"}"));
        assertThat(llama.getUsage().getInputTokens(), is(12));
        assertThat(llama.getUsage().getOutputTokens(), is(34));
        assertThat(llama.getUsage().getCachedInputTokens(), is(4));
        assertThat(llama.getUsage().getCacheCreationTokens(), is(2));
    }

    @Test
    public void bedrockCountsATitanTextBodyFromItsHeadersOrElseItsBody() {
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-titan-text.html
        String titanText = "{\"inputTextTokenCount\":5,\"results\":[{\"tokenCount\":9,"
            + "\"outputText\":\"\\nParis is the capital of France.\",\"completionReason\":\"FINISH\"}]}";
        // the headers win, so they are given different counts from the body's here
        Completion fromHeaders = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "6")
            .withHeader("X-Amzn-Bedrock-Output-Token-Count", "10")
            .withBody(titanText));
        assertThat(fromHeaders.getUsage().getInputTokens(), is(6));
        assertThat(fromHeaders.getUsage().getOutputTokens(), is(10));

        Completion fromBody = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(titanText));
        assertThat(fromBody.getUsage().getInputTokens(), is(5));
        assertThat(fromBody.getUsage().getOutputTokens(), is(9));
    }

    @Test
    public void bedrockCountsABodyWhoseUsageIsNotAnthropicsFromItsHeaders() {
        // gpt-oss on InvokeModel returns an OpenAI chat completion
        // https://docs.aws.amazon.com/bedrock/latest/userguide/model-parameters-openai.html
        Completion completion = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "21")
            .withHeader("X-Amzn-Bedrock-Output-Token-Count", "37")
            .withBody("{\"id\":\"chatcmpl-1f2e3d4c\",\"object\":\"chat.completion\",\"created\":1754400000,"
                + "\"model\":\"openai.gpt-oss-20b-1:0\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":\"<reasoning>The user asks.</reasoning>Sunny.\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":37,\"total_tokens\":58}}"));
        assertThat(completion.getUsage().getInputTokens(), is(21));
        assertThat(completion.getUsage().getOutputTokens(), is(37));
    }

    @Test
    public void bedrockCountsAnOpenAiChatCompletionFromItsBodyWithoutTheTokenCountHeaders() {
        // the /openai/v1/chat/completions endpoint and gpt-oss on InvokeModel return OpenAI's chat
        // completion object, and no Bedrock API reference documents the token-count headers on it
        // https://docs.aws.amazon.com/bedrock/latest/userguide/inference-chat-completions.html
        Completion completion = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withBody("{\"id\":\"chatcmpl-1f2e3d4c\",\"object\":\"chat.completion\",\"created\":1754400000,"
                + "\"model\":\"openai.gpt-oss-20b-1:0\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\","
                + "\"content\":\"Sunny.\"},\"finish_reason\":\"stop\"}],"
                + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":37,\"total_tokens\":58,"
                + "\"prompt_tokens_details\":{\"cached_tokens\":5},\"completion_tokens_details\":{\"reasoning_tokens\":12}}}"));
        assertThat(completion.getUsage().getInputTokens(), is(21));
        assertThat(completion.getUsage().getOutputTokens(), is(37));
        assertThat(completion.getUsage().getCachedInputTokens(), is(5));
        assertThat(completion.getUsage().getReasoningTokens(), is(12));
    }

    @Test
    public void bedrockCountsTheUsageChunkOfAStreamedOpenAiChatCompletion() {
        // with stream_options.include_usage the last chunk before [DONE] carries the usage
        LlmStreamUsageScanner scanner = new LlmStreamUsageScanner();
        scanner.accept(("data: {\"id\":\"chatcmpl-1f2e3d4c\",\"object\":\"chat.completion.chunk\",\"created\":1754400000,"
            + "\"model\":\"openai.gpt-oss-20b-1:0\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Sunny.\"},"
            + "\"finish_reason\":\"stop\"}],\"usage\":null}\n\n"
            + "data: {\"id\":\"chatcmpl-1f2e3d4c\",\"object\":\"chat.completion.chunk\",\"created\":1754400000,"
            + "\"model\":\"openai.gpt-oss-20b-1:0\",\"choices\":[],"
            + "\"usage\":{\"prompt_tokens\":21,\"completion_tokens\":37,\"total_tokens\":58}}\n\n"
            + "data: [DONE]\n\n").getBytes(StandardCharsets.UTF_8));

        Usage usage = new BedrockLlmClient().parseUsage(scanner.usage());
        assertThat(usage.getInputTokens(), is(21));
        assertThat(usage.getOutputTokens(), is(37));
    }

    @Test
    public void bedrockReadsAnthropicUsageBeforeOpenAiNamesInTheSameObject() {
        Usage usage = new BedrockLlmClient().parseUsage(ObjectMapperFactory.createObjectMapper().createObjectNode().put("input_tokens", 12).put("output_tokens", 3).put("prompt_tokens", 999));
        assertThat(usage.getInputTokens(), is(12));
        assertThat(usage.getOutputTokens(), is(3));
    }

    @Test
    public void geminiSaturatesAnOverflowingSumOfPredictedEmbeddingTokenCounts() {
        Completion completion = new GeminiLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"predictions\":[{\"embeddings\":{\"statistics\":{\"token_count\":9223372036854775807}}},"
                + "{\"embeddings\":{\"statistics\":{\"token_count\":9223372036854775807}}}]}"));
        assertThat(completion.getUsage().getInputTokens(), is(Integer.MAX_VALUE));
    }

    @Test
    public void bedrockPrefersTheUsageInTheBodyAndIgnoresAMalformedHeader() {
        Completion completion = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "999")
            .withBody("{\"content\":[{\"type\":\"text\",\"text\":\"Paris\"}],\"stop_reason\":\"end_turn\","
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}"));
        assertThat(completion.getUsage().getInputTokens(), is(12));

        Completion malformed = new BedrockLlmClient().parseCompletionResponse(response().withStatusCode(200)
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "-1")
            .withHeader("X-Amzn-Bedrock-Output-Token-Count", "many")
            .withBody("{\"results\":[{\"index\":0,\"relevance_score\":0.9}]}"));
        assertThat(malformed.getUsage(), is((org.mockserver.model.Usage) null));
    }

    // --- Vertex AI text embeddings :predict ---

    @Test
    public void geminiSumsTheTokenCountOfEachVertexAiPredictedEmbedding() {
        // https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/text-embeddings-api
        Completion completion = new GeminiLlmClient().parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"predictions\":[{\"embeddings\":{\"statistics\":{\"truncated\":false,\"token_count\":6},\"values\":[0.1,0.2]}},"
                + "{\"embeddings\":{\"statistics\":{\"truncated\":false,\"token_count\":4},\"values\":[0.3,0.4]}}],"
                + "\"metadata\":{\"billableCharacterCount\":40}}"));
        assertThat(completion.getUsage().getInputTokens(), is(10));
        assertThat(completion.getUsage().getOutputTokens(), is((Integer) null));
    }

    @Test
    public void backendRedactsApiKeyInToString() {
        LlmBackend backend = new LlmBackend("prod", Provider.OPENAI, null, "sk-secret-value", null,
            Collections.emptyMap(), null);
        assertThat(backend.toString(), containsString("***"));
        assertThat(backend.toString().contains("sk-secret-value"), is(false));
    }
}
