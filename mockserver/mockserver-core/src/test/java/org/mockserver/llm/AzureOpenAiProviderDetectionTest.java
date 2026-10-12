package org.mockserver.llm;

import org.junit.Test;
import org.mockserver.llm.client.LlmClient;
import org.mockserver.llm.client.LlmClientRegistry;
import org.mockserver.llm.client.LlmProviderSniffer;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * Azure OpenAI endpoints as Microsoft documents them (learn.microsoft.com
 * azure/ai-foundry/openai/api-version-lifecycle): the v1 GA base URL
 * {@code https://<resource>.openai.azure.com/openai/v1/} or
 * {@code https://<resource>.services.ai.azure.com/openai/v1/}, serving both
 * {@code /chat/completions} and {@code /responses}, whose usage is the OpenAI
 * {@code ResponseUsage} shape ({@code input_tokens}, {@code output_tokens}) in the Azure v1
 * OpenAPI spec (azure-rest-api-specs OpenAI.v1).
 */
public class AzureOpenAiProviderDetectionTest {

    private static HttpRequest to(String host, String path) {
        return request().withMethod("POST").withPath(path).withHeader("Host", host);
    }

    @Test
    public void detectorClassifiesTheAzureV1GaChatPathOnAnAzureHostAsAzureOpenAi() {
        assertThat(ProviderDetector.detect(to("myres.openai.azure.com", "/openai/v1/chat/completions")),
            is(Optional.of(Provider.AZURE_OPENAI)));
        assertThat(ProviderDetector.detect(to("myres.openai.azure.com:443", "/openai/deployments/gpt4o/chat/completions")),
            is(Optional.of(Provider.AZURE_OPENAI)));
        assertThat(ProviderDetector.detect(to("myres.services.ai.azure.com", "/openai/v1/chat/completions")),
            is(Optional.of(Provider.AZURE_OPENAI)));
        // the Foundry host also serves non-OpenAI APIs; those stay with path detection
        assertThat(ProviderDetector.detect(to("myres.services.ai.azure.com", "/models/chat/completions")),
            is(Optional.of(Provider.OPENAI)));
        // the same path on Groq's host, or with no host, is not Azure
        assertThat(ProviderDetector.detect(to("api.groq.com", "/openai/v1/chat/completions")), is(Optional.of(Provider.GROQ)));
        assertThat(ProviderDetector.detect(request().withPath("/openai/v1/chat/completions")), is(Optional.of(Provider.OPENAI)));
    }

    @Test
    public void detectorClassifiesAzureResponsesPathsAsTheResponsesApi() {
        assertThat(ProviderDetector.detect(to("myres.openai.azure.com", "/openai/v1/responses")),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
        assertThat(ProviderDetector.detect(to("myres.openai.azure.com", "/openai/responses")),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
        assertThat(ProviderDetector.detectFromPath("/openai/responses"), is(Optional.of(Provider.OPENAI_RESPONSES)));
        assertThat(ProviderDetector.detectFromPath("/myopenai/responses"), is(Optional.empty()));
    }

    @Test
    public void snifferClassifiesAzureResponsesTrafficAsTheResponsesApiAndAzureChatAsAzure() {
        assertThat(LlmProviderSniffer.sniffByHostAndPath("myres.openai.azure.com", "/openai/v1/responses"),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("myres.openai.azure.com", "/openai/responses"),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("myres.openai.azure.com", "/openai/deployments/gpt4o/chat/completions"),
            is(Optional.of(Provider.AZURE_OPENAI)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("myres.openai.azure.com", "/openai/v1/chat/completions"),
            is(Optional.of(Provider.AZURE_OPENAI)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("myres.services.ai.azure.com", "/openai/v1/chat/completions"),
            is(Optional.of(Provider.AZURE_OPENAI)));
        assertThat(LlmProviderSniffer.sniffByHostAndPath("myres.services.ai.azure.com", "/openai/v1/responses"),
            is(Optional.of(Provider.OPENAI_RESPONSES)));
        assertThat(LlmProviderSniffer.sniffByHost("myres.openai.azure.com"), is(Optional.of(Provider.AZURE_OPENAI)));
        assertThat(LlmProviderSniffer.sniffByPath(request().withPath("/openai/responses")), is(Optional.of(Provider.OPENAI_RESPONSES)));
    }

    @Test
    public void proxiedAzureResponsesUsageIsCounted() {
        Provider provider = LlmProviderSniffer.sniffByHostAndPath("myres.openai.azure.com", "/openai/v1/responses")
            .orElseThrow(AssertionError::new);
        LlmClient client = LlmClientRegistry.getInstance().lookup(provider).orElseThrow(AssertionError::new);
        Completion completion = client.parseCompletionResponse(response().withStatusCode(200).withBody(
            "{\"id\":\"resp_1\",\"object\":\"response\",\"status\":\"completed\",\"model\":\"gpt-4.1-nano\","
                + "\"output\":[{\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":\"hi\"}]}],"
                + "\"usage\":{\"input_tokens\":11,\"input_tokens_details\":{\"cached_tokens\":0},"
                + "\"output_tokens\":7,\"output_tokens_details\":{\"reasoning_tokens\":0},\"total_tokens\":18}}"));
        assertThat(completion.getUsage().getInputTokens(), is(11));
        assertThat(completion.getUsage().getOutputTokens(), is(7));
    }
}
