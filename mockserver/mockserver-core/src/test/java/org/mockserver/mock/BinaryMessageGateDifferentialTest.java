package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.configuration.Configuration;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.model.AnyOfRequestDefinition;
import org.mockserver.model.BinaryRequestDefinition;
import org.mockserver.model.BinaryResponse;
import org.mockserver.model.ConditionalRequestDefinition;
import org.mockserver.model.Not;
import org.mockserver.model.RequestDefinition;
import org.mockserver.scheduler.Scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * A proxied binary message is matched only when {@link HttpState#mayMatchBinaryMessage} says an expectation may match
 * it. Over seeded random stores and messages, that gate in front of the matcher pass picks the same expectation, in
 * the same order and with the same Times used up, as running the pass for every message.
 */
public class BinaryMessageGateDifferentialTest {

    private static final int SEEDS = 200;

    @Test
    public void shouldMatchTheSameExpectationsWithTheGateAsWithAMatcherPassForEveryMessage() {
        int gatedOut = 0;
        int matched = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            Random random = new Random(seed);
            List<Supplier<Expectation>> store = randomStore(random);
            List<byte[]> messages = messagesFor(store, random);
            Configuration configuration = configuration();
            HttpState gated = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), mock(Scheduler.class));
            HttpState everyMessage = new HttpState(configuration, new MockServerLogger(configuration, MockServerLogger.class), mock(Scheduler.class));
            try {
                addAll(gated, store);
                addAll(everyMessage, store);
                for (byte[] message : messages) {
                    String expected = idOf(matchAndPostProcess(everyMessage, message));
                    String actual = null;
                    if (gated.mayMatchBinaryMessage(message)) {
                        actual = idOf(matchAndPostProcess(gated, message));
                    } else {
                        gatedOut++;
                    }
                    if (expected != null) {
                        matched++;
                    }
                    assertThat("seed " + seed + ", message " + Arrays.toString(message), actual, is(expected));
                }
            } finally {
                gated.stop();
                everyMessage.stop();
            }
        }
        assertThat("the corpus exercises the gate", gatedOut, greaterThan(SEEDS));
        assertThat("the corpus exercises matches", matched, greaterThan(SEEDS));
    }

    private static void addAll(HttpState httpState, List<Supplier<Expectation>> store) {
        for (Supplier<Expectation> expectation : store) {
            httpState.add(expectation.get());
        }
    }

    private static Expectation matchAndPostProcess(HttpState httpState, byte[] message) {
        Expectation expectation = httpState.firstMatchingExpectation(BinaryRequestDefinition.binaryRequest(message));
        if (expectation != null) {
            httpState.postProcess(expectation);
        }
        return expectation;
    }

    private static String idOf(Expectation expectation) {
        return expectation != null ? expectation.getId() : null;
    }

    /**
     * Always one unlimited binary expectation, so the pass ran for every message before the gate; then a random mix of
     * binary expectations of short lengths (so lengths collide), negated ones, ones without bytes, conditional, anyOf and
     * HTTP ones, with random priorities and Times.
     */
    private static List<Supplier<Expectation>> randomStore(Random random) {
        List<Supplier<Expectation>> store = new ArrayList<>();
        store.add(binary("anchor", randomBytes(random, 1 + random.nextInt(4)), false, Times.unlimited(), 0));
        int size = 1 + random.nextInt(8);
        for (int i = 0; i < size; i++) {
            String id = "expectation-" + i;
            int priority = random.nextInt(3) - 1;
            Times times = random.nextBoolean() ? Times.unlimited() : Times.exactly(1 + random.nextInt(2));
            int kind = random.nextInt(20);
            if (kind < 12) {
                store.add(binary(id, randomBytes(random, 1 + random.nextInt(4)), false, times, priority));
            } else if (kind < 14) {
                store.add(binary(id, randomBytes(random, 1 + random.nextInt(4)), true, times, priority));
            } else if (kind < 15) {
                store.add(binary(id, random.nextBoolean() ? new byte[0] : null, false, times, priority));
            } else if (kind < 16) {
                store.add(expectation(id, ConditionalRequestDefinition.requestIf(request().withPath("/guard"), request().withPath("/then")), times, priority, false));
            } else if (kind < 17) {
                AnyOfRequestDefinition anyOf = AnyOfRequestDefinition.requestAnyOf(request().withPath("/a"), request().withPath("/b"));
                store.add(expectation(id, random.nextBoolean() ? Not.not(anyOf) : anyOf, times, priority, false));
            } else {
                store.add(expectation(id, request().withPath("/http-" + i), times, priority, false));
            }
        }
        return store;
    }

    private static Supplier<Expectation> binary(String id, byte[] bytes, boolean negated, Times times, int priority) {
        BinaryRequestDefinition binaryRequest = BinaryRequestDefinition.binaryRequest(bytes);
        return expectation(id, negated ? Not.not(binaryRequest) : binaryRequest, times, priority, true);
    }

    private static Supplier<Expectation> expectation(String id, RequestDefinition requestDefinition, Times times, int priority, boolean binaryResponse) {
        return () -> {
            Expectation expectation = new Expectation(requestDefinition.shallowClone(), times.clone(), TimeToLive.unlimited(), priority).withId(id);
            return binaryResponse
                ? expectation.thenRespondWithBinary(BinaryResponse.binaryResponse(id.getBytes()))
                : expectation.thenRespond(response());
        };
    }

    /** Each expectation's bytes and their near misses (a byte changed, one short, one long), empty and random. */
    private static List<byte[]> messagesFor(List<Supplier<Expectation>> store, Random random) {
        List<byte[]> messages = new ArrayList<>();
        for (Supplier<Expectation> supplier : store) {
            Expectation expectation = supplier.get();
            if (expectation.getHttpRequest() instanceof BinaryRequestDefinition) {
                byte[] bytes = ((BinaryRequestDefinition) expectation.getHttpRequest()).getBinaryData();
                if (bytes != null && bytes.length > 0) {
                    for (int copy = 0; copy < 3; copy++) {
                        messages.add(bytes.clone());
                    }
                    byte[] firstChanged = bytes.clone();
                    firstChanged[0] ^= 1;
                    messages.add(firstChanged);
                    byte[] lastChanged = bytes.clone();
                    lastChanged[lastChanged.length - 1] ^= (byte) 0x80;
                    messages.add(lastChanged);
                    messages.add(Arrays.copyOf(bytes, bytes.length - 1));
                    messages.add(Arrays.copyOf(bytes, bytes.length + 1));
                }
            }
        }
        messages.add(new byte[0]);
        for (int i = 0; i < 5; i++) {
            messages.add(randomBytes(random, 1 + random.nextInt(4)));
        }
        Collections.shuffle(messages, random);
        return messages;
    }

    private static byte[] randomBytes(Random random, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            // a small alphabet, so different expectations share bytes and lengths
            bytes[i] = (byte) (random.nextInt(3) == 0 ? 0xFF : random.nextInt(2));
        }
        return bytes;
    }
}
