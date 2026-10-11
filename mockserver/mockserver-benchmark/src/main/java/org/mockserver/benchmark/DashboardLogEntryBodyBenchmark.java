package org.mockserver.benchmark;

import org.mockserver.dashboard.model.DashboardBodyCap;
import org.mockserver.dashboard.model.DashboardLogEntryDTO;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.matchers.MatchType;
import org.mockserver.mock.Expectation;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.BodyWithContentType;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.JsonBody;
import org.mockserver.model.MediaType;
import org.mockserver.model.StringBody;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_MATCHED;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXPECTATION_RESPONSE;
import static org.mockserver.log.model.LogEntry.LogMessageType.RECEIVED_REQUEST;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * What a dashboard update spends turning ONE retained exchange into its rows: the received-request, matched and
 * response log entries, each built into a {@link DashboardLogEntryDTO} as every update does. The request and
 * response bodies are held as a retained entry holds them (raw bytes, no decoded view), so the cost includes
 * decoding (and, for JSON, parsing) each body for every place the exchange quotes it.
 *
 * <pre>./run.sh -prof gc DashboardLogEntryBodyBenchmark</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(value = 1, jvmArgsAppend = {"-Xmx4g"})
public class DashboardLogEntryBodyBenchmark {

    @Param({"JSON", "TEXT", "BINARY"})
    private String bodyType;

    @Param({"16384", "1048576", "10485760"})
    private int bodyBytes;

    // OFF: redactSecretsInLog off; HEADERS: on with no body fields (the default); FIELDS: on, masking a body field
    @Param({"OFF", "HEADERS", "FIELDS"})
    private String redaction;

    private Configuration configuration;
    private LogEntry received;
    private LogEntry matched;
    private LogEntry responded;

    @Setup
    public void setup() {
        configuration = configuration();
        if (!"OFF".equals(redaction)) {
            configuration.redactSecretsInLog(true).fixtureBodyRedactFields("FIELDS".equals(redaction) ? "password" : "");
        }
        HttpRequest request = request("/orders").withMethod("POST").withBody(body("request"));
        HttpResponse response = response().withStatusCode(200).withBody(body("response"));
        Expectation expectation = new Expectation(request("/orders")).thenRespond(response().withBody("{\"ok\":true}"));
        received = new LogEntry().setType(RECEIVED_REQUEST).setCorrelationId("c")
            .setHttpRequest(request)
            .setMessageFormat("received request:{}").setArguments(request);
        matched = new LogEntry().setType(EXPECTATION_MATCHED).setCorrelationId("c")
            .setHttpRequest(request).setExpectation(expectation)
            .setMessageFormat("request:{}matched expectation:{}").setArguments(request, expectation);
        responded = new LogEntry().setType(EXPECTATION_RESPONSE).setCorrelationId("c")
            .setHttpRequest(request).setHttpResponse(response)
            .setMessageFormat("returning response:{}for request:{}").setArguments(response, request);
        DashboardLogEntryDTO row = new DashboardLogEntryDTO(responded, configuration);
        if ((row.originalBodyLength(row.getHttpResponse()) != null) != (bodyBytes > DashboardBodyCap.MAX_BODY_CHARACTERS)) {
            throw new IllegalStateException("the response body was not read as the dashboard reads it");
        }
    }

    // Built from text and its bytes, then released, as the event log retains a body: only the bytes remain.
    private BodyWithContentType<?> body(String label) {
        if ("BINARY".equals(bodyType)) {
            byte[] bytes = new byte[bodyBytes];
            for (int i = 0; i < bytes.length; i++) {
                bytes[i] = (byte) (i * 31 + label.length());
            }
            return new BinaryBody(bytes, MediaType.parse("application/octet-stream"));
        }
        String text = "JSON".equals(bodyType) ? json(label) : text(label);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        BodyWithContentType<?> body = "JSON".equals(bodyType)
            ? new JsonBody(text, bytes, MediaType.APPLICATION_JSON_UTF_8, MatchType.ONLY_MATCHING_FIELDS)
            : new StringBody(text, bytes, false, MediaType.PLAIN_TEXT_UTF_8);
        body.releaseDerivedForms();
        if (body.retainedDerivedFormBytes() != 0) {
            throw new IllegalStateException("the body kept its decoded text");
        }
        return body;
    }

    private String json(String label) {
        StringBuilder json = new StringBuilder(bodyBytes + 256).append('[');
        for (int i = 0; json.length() < bodyBytes; i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"id\":").append(i)
                .append(",\"name\":\"").append(label).append("-café-").append(i)
                .append("\",\"price\":").append(i % 1000).append(".25")
                .append(",\"tags\":[\"a\",\"b\"],\"active\":").append(i % 2 == 0).append('}');
        }
        return json.append(']').toString();
    }

    private String text(String label) {
        StringBuilder text = new StringBuilder(bodyBytes + 64);
        for (int i = 0; text.length() < bodyBytes; i++) {
            text.append(label).append(" line ").append(i).append(" café\n");
        }
        return text.toString();
    }

    @Benchmark
    public void exchangeRows(Blackhole blackhole) {
        blackhole.consume(new DashboardLogEntryDTO(received, configuration));
        blackhole.consume(new DashboardLogEntryDTO(matched, configuration));
        blackhole.consume(new DashboardLogEntryDTO(responded, configuration));
    }
}
