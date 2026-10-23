package org.mockserver.llm.drift;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.llm.ParsedConversation;
import org.mockserver.llm.ProviderCodec;
import org.mockserver.llm.ProviderCodecRegistry;
import org.mockserver.llm.client.LlmBackend;
import org.mockserver.llm.client.LlmClient;
import org.mockserver.llm.client.LlmClientRegistry;
import org.mockserver.llm.client.LlmTransport;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.Provider;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Replays recorded LLM request/response exchanges against a live provider and
 * reports <em>structural</em> drift in the responses (new/removed fields, type
 * changes) — closing the loop on stale VCR cassettes.
 * <p>
 * Drift is structural, not semantic: it compares response <em>shape</em>, not
 * values, so it never flags benign wording changes. Each exchange fails closed
 * independently — a network error or non-2xx live response is reported as
 * {@code COULD_NOT_CHECK}, never as drift and never thrown. The detector takes
 * an injected {@link LlmTransport} so it unit-tests offline; the live HTTP call
 * is only made when a backend is configured (opt-in).
 */
public class DriftDetector {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    /** A recorded request and the body of its recorded response. */
    public record RecordedExchange(HttpRequest recordedRequest, String recordedResponseBody) {
    }

    private final LlmClientRegistry clientRegistry;
    private final ProviderCodecRegistry codecRegistry;
    private final LlmTransport transport;
    private final long timeoutMillis;

    public DriftDetector(LlmTransport transport, long timeoutMillis) {
        this(LlmClientRegistry.getInstance(), ProviderCodecRegistry.getInstance(), transport, timeoutMillis);
    }

    public DriftDetector(LlmClientRegistry clientRegistry, ProviderCodecRegistry codecRegistry,
                         LlmTransport transport, long timeoutMillis) {
        this.clientRegistry = clientRegistry;
        this.codecRegistry = codecRegistry;
        this.transport = transport;
        this.timeoutMillis = timeoutMillis;
    }

    /**
     * Replay each recorded exchange against {@code backend} and diff the live
     * response shape against the recorded one. Never throws.
     * <p>
     * When {@code provider} is not the backend's own provider nothing is sent: another provider's
     * client would carry the backend's API key to that provider's default host.
     */
    public DriftReport detect(List<RecordedExchange> exchanges, Provider provider, LlmBackend backend) {
        List<DriftReport.ExchangeDrift> results = new ArrayList<>();
        if (backend == null || backend.provider() != provider) {
            String note = "cassette provider " + provider + " is not the configured backend provider "
                + (backend == null ? null : backend.provider()) + ", so it was not sent";
            for (int i = 0; i < exchanges.size(); i++) {
                results.add(couldNotCheck(i, note));
            }
            return new DriftReport(results);
        }
        Optional<LlmClient> clientOpt = clientRegistry.lookup(provider);
        Optional<ProviderCodec> codecOpt = codecRegistry.lookup(provider);

        for (int i = 0; i < exchanges.size(); i++) {
            RecordedExchange exchange = exchanges.get(i);
            try {
                if (!clientOpt.isPresent() || !codecOpt.isPresent()) {
                    results.add(couldNotCheck(i, "no runtime client/codec registered for provider " + provider));
                    continue;
                }
                ParsedConversation conversation = codecOpt.get().decode(exchange.recordedRequest());
                HttpRequest liveRequest = clientOpt.get().buildCompletionRequest(backend, conversation);
                HttpResponse liveResponse = transport.send(liveRequest, timeoutMillis);
                Integer status = liveResponse == null ? null : liveResponse.getStatusCode();
                if (status == null || status < 200 || status >= 300) {
                    results.add(couldNotCheck(i, "live provider call returned " + (status == null ? "no response" : status)));
                    continue;
                }
                JsonNode recorded = parse(exchange.recordedResponseBody());
                JsonNode live = parse(liveResponse.getBodyAsText());
                if (recorded == null || live == null) {
                    results.add(couldNotCheck(i, "recorded or live response body was not JSON"));
                    continue;
                }
                StructuralShapeDiff.ShapeDiff diff = StructuralShapeDiff.diff(recorded, live);
                results.add(new DriftReport.ExchangeDrift(i,
                    diff.hasDrift() ? DriftReport.ExchangeDrift.Status.DRIFT : DriftReport.ExchangeDrift.Status.NO_DRIFT,
                    diff.getAddedPaths(), diff.getRemovedPaths(), diff.getTypeChangedPaths(), null));
            } catch (Exception e) {
                results.add(couldNotCheck(i, e.getMessage()));
            }
        }
        return new DriftReport(results);
    }

    private static DriftReport.ExchangeDrift couldNotCheck(int index, String note) {
        return new DriftReport.ExchangeDrift(index, DriftReport.ExchangeDrift.Status.COULD_NOT_CHECK,
            Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), note);
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readTree(body);
        } catch (Exception e) {
            return null;
        }
    }
}
