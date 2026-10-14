package org.mockserver.benchmark;

import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.Configuration;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.mock.Expectation;
import org.mockserver.mock.RequestMatchers;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.scheduler.Scheduler;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.mock;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.BinaryResponse.binaryResponse;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * The per-message cost of matching a relayed binary message that no expectation matches, with
 * {@code matchBinaryExpectationsWhenProxying} on and binary expectations registered.
 *
 * <p>Both arms run in one JMH run, so they share a machine:
 * <ul>
 *   <li>{@code everyMessage}: the matcher pass every relayed message got before the gate.</li>
 *   <li>{@code gated}: the gate ({@link RequestMatchers#mayMatchBinaryMessage}) and the pass only when the gate
 *       says an expectation may match, which it does not for this message.</li>
 * </ul>
 *
 * <p>{@code messageLength} 32 is the gate's worst case (every binary expectation has 32 bytes, so each is compared);
 * 200 is its usual case (no binary expectation of that length). Logging is at WARN, so neither arm writes match
 * entries; at INFO the old pass also logged one entry per binary expectation and a closest-match entry per message.
 *
 * <pre>./run.sh BinaryRelayMatchBenchmark -prof gc</pre>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class BinaryRelayMatchBenchmark {

    @Param({"0", "1000"})
    public int httpExpectations;

    @Param({"1", "50"})
    public int binaryExpectations;

    @Param({"32", "200"})
    public int messageLength;

    private RequestMatchers requestMatchers;
    private byte[] message;

    @Setup(Level.Trial)
    public void setup() {
        ConfigurationProperties.logLevel("WARN");
        ConfigurationProperties.detailedMatchFailures(false);

        Configuration configuration = Configuration.configuration();
        requestMatchers = new RequestMatchers(configuration, new MockServerLogger(), mock(Scheduler.class), mock(WebSocketClientRegistry.class));
        for (int i = 0; i < httpExpectations; i++) {
            requestMatchers.add(new Expectation(request().withMethod("GET").withPath("/data/path-" + i)).thenRespond(response().withBody("e" + i)), API);
        }
        for (int i = 0; i < binaryExpectations; i++) {
            byte[] bytes = new byte[32];
            Arrays.fill(bytes, (byte) i);
            requestMatchers.add(new Expectation(BinaryRequestDefinition.binaryRequest(bytes)).thenRespondWithBinary(binaryResponse(new byte[]{1})), API);
        }
        message = new byte[messageLength];
        Arrays.fill(message, (byte) 0x7F);
    }

    @Benchmark
    public Expectation everyMessage() {
        return requestMatchers.firstMatchingExpectation(BinaryRequestDefinition.binaryRequest(message));
    }

    @Benchmark
    public Expectation gated() {
        return requestMatchers.mayMatchBinaryMessage(message) ? requestMatchers.firstMatchingExpectation(BinaryRequestDefinition.binaryRequest(message)) : null;
    }
}
