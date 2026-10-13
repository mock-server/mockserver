package org.mockserver.mock.action.http;

import org.junit.Test;
import org.mockserver.model.Completion;
import org.mockserver.model.HttpResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpResponse.response;

public class EncodedLlmCallUsageTest {

    private static final String COHERE_BODY = "{\"id\":\"e1\",\"embeddings\":[[0.1,0.2]],\"response_type\":\"embeddings_floats\",\"texts\":[\"hi\"]}";

    @Test
    public void shouldCountABodyWithNoUsageFromTheBedrockInputTokenCountHeader() {
        Completion usage = EncodedLlmCallUsage.read(response(COHERE_BODY).withHeader("X-Amzn-Bedrock-Input-Token-Count", "7"));

        assertThat(usage.getUsage().getInputTokens(), is(7));
        assertThat(usage.getUsage().getOutputTokens(), nullValue());
    }

    @Test
    public void shouldReadTheHeaderWhateverItsCase() {
        Completion usage = EncodedLlmCallUsage.read(response(COHERE_BODY).withHeader("x-amzn-bedrock-input-token-count", " 3 "));

        assertThat(usage.getUsage().getInputTokens(), is(3));
    }

    @Test
    public void shouldPreferTheBodyCountOverTheHeader() {
        Completion usage = EncodedLlmCallUsage.read(response("{\"embedding\":[0.1],\"inputTextTokenCount\":4}")
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "99"));

        assertThat(usage.getUsage().getInputTokens(), is(4));
    }

    @Test
    public void shouldKeepTheBodyModelWhenCountingFromTheHeader() {
        Completion usage = EncodedLlmCallUsage.read(response("{\"model\":\"m1\",\"data\":[]}")
            .withHeader("X-Amzn-Bedrock-Input-Token-Count", "5"));

        assertThat(usage.getModel(), is("m1"));
        assertThat(usage.getUsage().getInputTokens(), is(5));
    }

    @Test
    public void shouldCountFromTheHeaderWhenTheBodyIsNotAJsonObject() {
        Completion usage = EncodedLlmCallUsage.read(response("[]").withHeader("X-Amzn-Bedrock-Input-Token-Count", "2"));

        assertThat(usage.getUsage().getInputTokens(), is(2));
    }

    @Test
    public void shouldClampAHeaderCountBeyondTheIntegerRange() {
        Completion usage = EncodedLlmCallUsage.read(response(COHERE_BODY).withHeader("X-Amzn-Bedrock-Input-Token-Count", "9999999999"));

        assertThat(usage.getUsage().getInputTokens(), is(Integer.MAX_VALUE));
    }

    @Test
    public void shouldIgnoreAHeaderThatIsNotACount() {
        for (String value : new String[]{"-1", "many", "", "1.5"}) {
            HttpResponse encoded = response(COHERE_BODY).withHeader("X-Amzn-Bedrock-Input-Token-Count", value);

            assertThat(value, EncodedLlmCallUsage.read(encoded).getUsage(), nullValue());
        }
        assertThat(EncodedLlmCallUsage.read(response("[]").withHeader("X-Amzn-Bedrock-Input-Token-Count", "many")), nullValue());
    }

    @Test
    public void shouldReportNoUsageWithNeitherABodyCountNorAHeader() {
        assertThat(EncodedLlmCallUsage.read(response(COHERE_BODY)).getUsage(), nullValue());
    }
}
