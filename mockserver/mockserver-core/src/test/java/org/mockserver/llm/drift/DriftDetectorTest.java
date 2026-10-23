package org.mockserver.llm.drift;

import org.junit.Test;
import org.mockserver.llm.ProviderCodecRegistry;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmClientRegistry;
import org.mockserver.llm.client.LlmTransport;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.Provider;

import java.util.Collections;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class DriftDetectorTest {

    // Anthropic-shaped recorded request and response.
    private static final HttpRequest RECORDED_REQUEST = request().withBody(
        "{\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}");
    private static final String RECORDED_RESPONSE =
        "{\"content\":[{\"type\":\"text\",\"text\":\"hello\"}],\"stop_reason\":\"end_turn\"," +
            "\"usage\":{\"input_tokens\":1,\"output_tokens\":1}}";

    private static List<DriftDetector.RecordedExchange> oneExchange() {
        return Collections.singletonList(new DriftDetector.RecordedExchange(RECORDED_REQUEST, RECORDED_RESPONSE));
    }

    private static LlmBackend backend() {
        return LlmBackend.of(Provider.ANTHROPIC, "ak-test");
    }

    @Test
    public void reportsNoDriftWhenLiveShapeMatchesRecorded() {
        LlmTransport transport = (req, timeout) -> response().withStatusCode(200).withBody(
            "{\"content\":[{\"type\":\"text\",\"text\":\"different words\"}],\"stop_reason\":\"end_turn\"," +
                "\"usage\":{\"input_tokens\":9,\"output_tokens\":9}}");
        DriftReport report = new DriftDetector(transport, 1000).detect(oneExchange(), Provider.ANTHROPIC, backend());
        assertThat(report.hasDrift(), is(false));
        assertThat(report.checkedCount(), is(1L));
        assertThat(report.getExchanges().get(0).getStatus(), is(DriftReport.ExchangeDrift.Status.NO_DRIFT));
    }

    @Test
    public void reportsDriftWhenLiveHasNewField() {
        LlmTransport transport = (req, timeout) -> response().withStatusCode(200).withBody(
            "{\"content\":[{\"type\":\"text\",\"text\":\"hi\"}],\"stop_reason\":\"end_turn\"," +
                "\"usage\":{\"input_tokens\":1,\"output_tokens\":1},\"cache_creation_input_tokens\":5}");
        DriftReport report = new DriftDetector(transport, 1000).detect(oneExchange(), Provider.ANTHROPIC, backend());
        assertThat(report.hasDrift(), is(true));
        assertThat(report.getExchanges().get(0).getAddedPaths().contains("$.cache_creation_input_tokens"), is(true));
    }

    @Test
    public void failsClosedOnTransportException() {
        LlmTransport transport = (req, timeout) -> {
            throw new RuntimeException("connection refused");
        };
        DriftReport report = new DriftDetector(transport, 1000).detect(oneExchange(), Provider.ANTHROPIC, backend());
        assertThat(report.couldNotCheckCount(), is(1L));
        assertThat(report.hasDrift(), is(false));
    }

    @Test
    public void failsClosedOnNon2xx() {
        LlmTransport transport = (req, timeout) -> response().withStatusCode(500).withBody("err");
        DriftReport report = new DriftDetector(transport, 1000).detect(oneExchange(), Provider.ANTHROPIC, backend());
        assertThat(report.couldNotCheckCount(), is(1L));
    }

    @Test
    public void sendsNothingWhenTheCassetteProviderIsNotTheBackendProvider() {
        // with no baseUrl the OPENAI client would carry the Anthropic key to api.openai.com
        List<HttpRequest> sent = new java.util.concurrent.CopyOnWriteArrayList<>();
        LlmTransport transport = (req, timeout) -> {
            sent.add(req);
            return response().withStatusCode(200).withBody("{}");
        };
        DriftReport report = new DriftDetector(transport, 1000).detect(oneExchange(), Provider.OPENAI, backend());
        assertThat(sent.isEmpty(), is(true));
        assertThat(report.couldNotCheckCount(), is(1L));
        assertThat(report.getExchanges().get(0).getNote().contains("was not sent"), is(true));
    }

    @Test
    public void couldNotCheckWhenNoClientForProvider() {
        // empty registries → no client/codec
        LlmTransport transport = (req, timeout) -> response().withStatusCode(200).withBody("{}");
        DriftReport report = new DriftDetector(new LlmClientRegistry(), new ProviderCodecRegistry(), transport, 1000)
            .detect(oneExchange(), Provider.ANTHROPIC, backend());
        assertThat(report.couldNotCheckCount(), is(1L));
    }
}
