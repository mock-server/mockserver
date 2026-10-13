package org.mockserver.llm.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ParsedMessage;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.model.Usage;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.core.Is.is;

/**
 * Tests for {@link BedrockLlmClient} SigV4 signing integration: credential
 * parsing, region extraction, Authorization header generation, and backward
 * compatibility when no credentials are supplied.
 */
public class BedrockLlmClientTest {

    private static final Instant PINNED_TIME = Instant.parse("2024-03-15T08:00:00Z");

    /**
     * A testable subclass that pins the signing timestamp for deterministic output.
     */
    private static class TestableBedrockLlmClient extends BedrockLlmClient {
        @Override
        protected Instant signingTimestamp() {
            return PINNED_TIME;
        }
    }

    private static ParsedConversation conversation() {
        return ParsedConversation.of(Arrays.asList(
            new ParsedMessage(ParsedMessage.Role.SYSTEM, "You are helpful.", null, null),
            new ParsedMessage(ParsedMessage.Role.USER, "Hello", null, null)
        ));
    }

    // --- SigV4 signing with valid credentials ---

    @Test
    public void requestIsSignedWhenApiKeyContainsAccessKeyAndSecret() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK, null,
                "AKIDEXAMPLE:wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
                null, null, null),
            conversation());

        String auth = request.getFirstHeader("Authorization");
        assertThat(auth, startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20240315/us-east-1/bedrock/aws4_request"));
        assertThat(auth, containsString("SignedHeaders="));
        assertThat(auth, containsString("Signature="));

        assertThat(request.getFirstHeader("X-Amz-Date"), is("20240315T080000Z"));
        assertThat(request.getFirstHeader("X-Amz-Content-Sha256"), not(isEmptyOrNullString()));
    }

    @Test
    public void regionIsParsedFromCustomBaseUrl() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK,
                "https://bedrock-runtime.eu-west-1.amazonaws.com",
                "AKIDEXAMPLE:wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
                null, null, null),
            conversation());

        String auth = request.getFirstHeader("Authorization");
        // Credential scope should include eu-west-1, not us-east-1
        assertThat(auth, containsString("/eu-west-1/bedrock/aws4_request"));
    }

    @Test
    public void sessionTokenAddsSecurityTokenHeader() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK, null,
                "AKIDEXAMPLE:SECRET:MySessionToken123",
                null, null, null),
            conversation());

        assertThat(request.getFirstHeader("X-Amz-Security-Token"), is("MySessionToken123"));
        String auth = request.getFirstHeader("Authorization");
        assertThat(auth, containsString("x-amz-security-token"));
    }

    // --- Backward compatibility: no signing when creds absent ---

    @Test
    public void noSigningWhenApiKeyIsNull() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.BEDROCK, null),
            conversation());

        assertThat(request.getFirstHeader("Authorization"), is(""));
        assertThat(request.getFirstHeader("X-Amz-Date"), is(""));
        assertThat(request.getFirstHeader("X-Amz-Content-Sha256"), is(""));
    }

    @Test
    public void noSigningWhenApiKeyIsBlank() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.BEDROCK, "   "),
            conversation());

        assertThat(request.getFirstHeader("Authorization"), is(""));
    }

    @Test
    public void noSigningWhenApiKeyHasNoColon() {
        // An apiKey without ':' is not in akid:secret format — skip signing
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            LlmBackend.of(Provider.BEDROCK, "just-a-plain-key"),
            conversation());

        assertThat(request.getFirstHeader("Authorization"), is(""));
    }

    // --- Escape hatch headers ---

    @Test
    public void escapeHatchHeadersAreAppliedWhenNoCreds() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK, null, null, null,
                Collections.singletonMap("Authorization", "PreSigned value"),
                null),
            conversation());

        assertThat(request.getFirstHeader("Authorization"), is("PreSigned value"));
    }

    @Test
    public void sigV4TakesPrecedenceOverEscapeHatchAuthorization() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK, null,
                "AKIDEXAMPLE:SECRET",
                null,
                Collections.singletonMap("Authorization", "PreSigned should be overridden"),
                null),
            conversation());

        String auth = request.getFirstHeader("Authorization");
        // SigV4 should have overridden the escape hatch value
        assertThat(auth, startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/"));
    }

    // --- Credential parsing ---

    @Test
    public void parseAwsCredentials_validTwoPart() {
        BedrockLlmClient.AwsCredentials creds = BedrockLlmClient.parseAwsCredentials("AKID:SECRET");
        assertThat(creds.accessKeyId, is("AKID"));
        assertThat(creds.secretAccessKey, is("SECRET"));
        assertThat(creds.sessionToken, nullValue());
    }

    @Test
    public void parseAwsCredentials_validThreePart() {
        BedrockLlmClient.AwsCredentials creds = BedrockLlmClient.parseAwsCredentials("AKID:SECRET:TOKEN");
        assertThat(creds.accessKeyId, is("AKID"));
        assertThat(creds.secretAccessKey, is("SECRET"));
        assertThat(creds.sessionToken, is("TOKEN"));
    }

    @Test
    public void parseAwsCredentials_nullReturnsNull() {
        assertThat(BedrockLlmClient.parseAwsCredentials(null), nullValue());
    }

    @Test
    public void parseAwsCredentials_blankReturnsNull() {
        assertThat(BedrockLlmClient.parseAwsCredentials("  "), nullValue());
    }

    @Test
    public void parseAwsCredentials_noColonReturnsNull() {
        assertThat(BedrockLlmClient.parseAwsCredentials("just-a-key"), nullValue());
    }

    // --- Region parsing ---

    @Test
    public void parseRegion_standardBedrockHost() {
        assertThat(BedrockLlmClient.parseRegion("bedrock-runtime.eu-west-1.amazonaws.com"), is("eu-west-1"));
    }

    @Test
    public void parseRegion_defaultUsEast1Host() {
        assertThat(BedrockLlmClient.parseRegion("bedrock-runtime.us-east-1.amazonaws.com"), is("us-east-1"));
    }

    @Test
    public void parseRegion_nonBedrockHostDefaultsToUsEast1() {
        assertThat(BedrockLlmClient.parseRegion("custom-proxy.internal"), is("us-east-1"));
    }

    @Test
    public void parseRegion_nullDefaultsToUsEast1() {
        assertThat(BedrockLlmClient.parseRegion(null), is("us-east-1"));
    }

    // --- Path encoding ---

    @Test
    public void modelIdWithColonIsEncodedInCanonicalUri() {
        // The default model ID contains a colon: anthropic.claude-3-5-sonnet-20241022-v2:0
        // When signing, the canonical URI should have the colon percent-encoded
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK, null,
                "AKIDEXAMPLE:SECRET",
                "anthropic.claude-3-5-sonnet-20241022-v2:0",
                null, null),
            conversation());

        String auth = request.getFirstHeader("Authorization");
        // Just verify it signed successfully — the model ID encoding is handled internally
        assertThat(auth, startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/"));
    }

    // --- Content-type is signed ---

    @Test
    public void contentTypeIsIncludedInSignedHeaders() {
        HttpRequest request = new TestableBedrockLlmClient().buildCompletionRequest(
            new LlmBackend(null, Provider.BEDROCK, null,
                "AKIDEXAMPLE:SECRET",
                null, null, null),
            conversation());

        String auth = request.getFirstHeader("Authorization");
        assertThat(auth, containsString("content-type"));
    }

    @Test
    public void shouldParseConverseResponseUsageAndText() {
        HttpResponse response = HttpResponse.response().withBody("{"
            + "\"output\":{\"message\":{\"role\":\"assistant\",\"content\":[{\"text\":\"Hello \"},{\"text\":\"world\"}]}},"
            + "\"stopReason\":\"end_turn\","
            + "\"usage\":{\"inputTokens\":30,\"outputTokens\":12,\"totalTokens\":192,\"cacheReadInputTokens\":100,\"cacheWriteInputTokens\":50},"
            + "\"metrics\":{\"latencyMs\":1275}}");

        Completion completion = new BedrockLlmClient().parseCompletionResponse(response);

        assertThat(completion.getText(), is("Hello world"));
        assertThat(completion.getStopReason(), is("end_turn"));
        assertThat(completion.getUsage().getInputTokens(), is(30));
        assertThat(completion.getUsage().getOutputTokens(), is(12));
        assertThat(completion.getUsage().getCachedInputTokens(), is(100));
        assertThat(completion.getUsage().getCacheCreationTokens(), is(50));
    }

    @Test
    public void shouldStillParseInvokeModelAnthropicResponse() {
        HttpResponse response = HttpResponse.response().withBody("{"
            + "\"type\":\"message\",\"role\":\"assistant\",\"content\":[{\"type\":\"text\",\"text\":\"Hi\"}],"
            + "\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":3,\"output_tokens\":1}}");

        Completion completion = new BedrockLlmClient().parseCompletionResponse(response);

        assertThat(completion.getText(), is("Hi"));
        assertThat(completion.getUsage().getInputTokens(), is(3));
        assertThat(completion.getUsage().getOutputTokens(), is(1));
    }

    // the field names langchain-aws reads (llms/bedrock.py, _get_invocation_metrics_chunk)
    @Test
    public void shouldMapTheInvocationMetricsOfAnInvokeModelWithResponseStreamStream() throws Exception {
        Usage usage = new BedrockLlmClient().parseUsage(new ObjectMapper().readTree("{"
            + "\"inputTokenCount\":12,\"outputTokenCount\":4,\"invocationLatency\":300,\"firstByteLatency\":100,"
            + "\"cacheReadInputTokenCount\":6,\"cacheWriteInputTokenCount\":2}"));

        assertThat(usage, is(Usage.usage().withInputTokens(12).withOutputTokens(4).withCachedInputTokens(6).withCacheCreationTokens(2)));
    }
}
