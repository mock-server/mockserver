package org.mockserver.mock.action.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.configuration.Configuration;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class ForwardCircuitBreakerTest {

    private final AtomicLong clock = new AtomicLong(1_000L);
    private ForwardCircuitBreaker breaker;
    private Configuration configuration;

    @Before
    public void setup() {
        breaker = new ForwardCircuitBreaker(clock::get);
        configuration = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(3)
            .forwardProxyCircuitBreakerWindowMillis(10_000L);
    }

    @After
    public void teardown() {
        breaker.reset();
    }

    private static String key() {
        return ForwardCircuitBreaker.keyFor(new InetSocketAddress("upstream.example", 8080));
    }

    @Test
    public void shouldBuildKeyFromAddress() {
        assertThat(ForwardCircuitBreaker.keyFor(InetSocketAddress.createUnresolved("h", 80)), is("h:80"));
        assertThat(ForwardCircuitBreaker.keyFor(null), is(nullValue()));
    }

    @Test
    public void shouldAllowAllRequestsWhenDisabled() {
        Configuration disabled = Configuration.configuration().forwardProxyCircuitBreakerEnabled(false);
        for (int i = 0; i < 100; i++) {
            breaker.recordFailure(disabled, key());
            assertThat(breaker.allowRequest(disabled, key()), is(true));
        }
        assertThat(breaker.openCircuitCount(), is(0));
    }

    @Test
    public void shouldAllowWhenKeyIsNull() {
        assertThat(breaker.allowRequest(configuration, null), is(true));
    }

    @Test
    public void shouldOpenAfterThresholdConsecutiveFailures() {
        // given - 2 failures (threshold 3) => still closed
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        assertThat(breaker.allowRequest(configuration, key()), is(true));
        assertThat(breaker.isOpen(key()), is(false));

        // when - third consecutive failure
        breaker.recordFailure(configuration, key());

        // then - circuit opens and fails fast
        assertThat(breaker.isOpen(key()), is(true));
        assertThat(breaker.allowRequest(configuration, key()), is(false));
        assertThat(breaker.openCircuitCount(), is(1));
    }

    @Test
    public void shouldResetFailureCountOnSuccess() {
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordSuccess(configuration, key());

        // after success, two more failures should not open (count was reset)
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        assertThat(breaker.isOpen(key()), is(false));
        assertThat(breaker.allowRequest(configuration, key()), is(true));
    }

    @Test
    public void shouldRecoverAfterWindowViaHalfOpenSuccess() {
        // given - open the breaker
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        assertThat(breaker.allowRequest(configuration, key()), is(false));

        // when - window elapses
        clock.addAndGet(10_001L);

        // then - exactly one trial request is allowed (half-open)
        assertThat(breaker.allowRequest(configuration, key()), is(true));
        // concurrent second request in the same window is rejected
        assertThat(breaker.allowRequest(configuration, key()), is(false));

        // a successful trial closes the breaker
        breaker.recordSuccess(configuration, key());
        assertThat(breaker.isOpen(key()), is(false));
        assertThat(breaker.allowRequest(configuration, key()), is(true));
        assertThat(breaker.openCircuitCount(), is(0));
    }

    @Test
    public void shouldReopenAfterFailedHalfOpenTrial() {
        // given - open the breaker
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());

        // window elapses, trial allowed
        clock.addAndGet(10_001L);
        assertThat(breaker.allowRequest(configuration, key()), is(true));

        // when - the trial fails
        breaker.recordFailure(configuration, key());

        // then - breaker re-opens for another window, failing fast again
        assertThat(breaker.isOpen(key()), is(true));
        assertThat(breaker.allowRequest(configuration, key()), is(false));

        // after another window, half-open trial is allowed again
        clock.addAndGet(10_001L);
        assertThat(breaker.allowRequest(configuration, key()), is(true));
    }

    @Test
    public void shouldNotResetTheFailureCountOnANeutralOutcome() {
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordNeutral(configuration, key());
        breaker.recordNeutral(configuration, key());
        assertThat(breaker.isOpen(key()), is(false));

        breaker.recordFailure(configuration, key());

        assertThat(breaker.isOpen(key()), is(true));
    }

    @Test
    public void shouldNotOpenOnNeutralOutcomesAlone() {
        for (int i = 0; i < 10; i++) {
            breaker.recordNeutral(configuration, key());
        }

        assertThat(breaker.isOpen(key()), is(false));
        assertThat(breaker.allowRequest(configuration, key()), is(true));
        assertThat(breaker.trackedUpstreamCount(), is(0));
    }

    @Test
    public void shouldKeepAHalfOpenBreakerOpenAndReleaseItsTrialOnANeutralOutcome() {
        // given - open, then half-open with the trial taken
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        clock.addAndGet(10_001L);
        assertThat(breaker.allowRequest(configuration, key()), is(true));
        assertThat(breaker.allowRequest(configuration, key()), is(false));

        // when - the trial's outcome says nothing about the upstream
        breaker.recordNeutral(configuration, key());

        // then - still open, and the next request is the trial
        assertThat(breaker.isOpen(key()), is(true));
        assertThat(breaker.openCircuitCount(), is(1));
        assertThat(breaker.allowRequest(configuration, key()), is(true));
        assertThat(breaker.allowRequest(configuration, key()), is(false));

        // and - that trial's failure re-opens the breaker for a whole window
        breaker.recordFailure(configuration, key());
        assertThat(breaker.allowRequest(configuration, key()), is(false));
        clock.addAndGet(9_999L);
        assertThat(breaker.allowRequest(configuration, key()), is(false));
    }

    @Test
    public void shouldIgnoreANeutralOutcomeWhenDisabled() {
        Configuration disabled = Configuration.configuration().forwardProxyCircuitBreakerEnabled(false);
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        clock.addAndGet(10_001L);
        assertThat(breaker.allowRequest(configuration, key()), is(true));

        breaker.recordNeutral(disabled, key());
        breaker.recordNeutral(configuration, null);

        assertThat(breaker.allowRequest(configuration, key()), is(false));
    }

    @Test
    public void shouldKeySeparatelyPerUpstream() {
        String a = ForwardCircuitBreaker.keyFor(new InetSocketAddress("a.example", 80));
        String b = ForwardCircuitBreaker.keyFor(new InetSocketAddress("b.example", 80));

        breaker.recordFailure(configuration, a);
        breaker.recordFailure(configuration, a);
        breaker.recordFailure(configuration, a);

        assertThat(breaker.allowRequest(configuration, a), is(false));
        assertThat(breaker.allowRequest(configuration, b), is(true));
        assertThat(breaker.openCircuitCount(), is(1));
    }

    @Test
    public void shouldEvictHealthyUpstreamsToBoundTheMap() {
        // given - a transient blip on one upstream then a success clears and evicts it
        breaker.recordFailure(configuration, key());
        breaker.recordSuccess(configuration, key());
        assertThat(breaker.trackedUpstreamCount(), is(0));

        // and - a steady stream of successes to many distinct keys (the Host-header growth vector)
        for (int i = 0; i < 1000; i++) {
            String k = ForwardCircuitBreaker.keyFor(InetSocketAddress.createUnresolved("h" + i + ".example", 80));
            breaker.recordFailure(configuration, k);
            breaker.recordSuccess(configuration, k);
        }

        // then - the map does not grow with distinct healthy keys
        assertThat(breaker.trackedUpstreamCount(), is(0));
    }

    @Test
    public void shouldRetainOpenUpstreamAcrossSuccessesOnOtherUpstreams() {
        // given - upstream a is open
        String a = ForwardCircuitBreaker.keyFor(new InetSocketAddress("a.example", 80));
        breaker.recordFailure(configuration, a);
        breaker.recordFailure(configuration, a);
        breaker.recordFailure(configuration, a);
        assertThat(breaker.isOpen(a), is(true));

        // when - other upstreams succeed (and are evicted)
        String b = ForwardCircuitBreaker.keyFor(new InetSocketAddress("b.example", 80));
        breaker.recordFailure(configuration, b);
        breaker.recordSuccess(configuration, b);

        // then - the open upstream is retained, only the healthy one was evicted
        assertThat(breaker.isOpen(a), is(true));
        assertThat(breaker.trackedUpstreamCount(), is(1));
    }

    @Test
    public void shouldResetAllState() {
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        breaker.recordFailure(configuration, key());
        assertThat(breaker.openCircuitCount(), is(1));

        breaker.reset();

        assertThat(breaker.openCircuitCount(), is(0));
        assertThat(breaker.allowRequest(configuration, key()), is(true));
    }

    @Test
    public void shouldEscapeLineBreaksInTheUpstreamKeyWhenLogging() {
        String marker = "forged-breaker-entry";
        String key = ForwardCircuitBreaker.keyFor(InetSocketAddress.createUnresolved("evil\r\n2026-01-01 INFO " + marker, 80));
        Configuration tripOnFirstFailure = Configuration.configuration()
            .forwardProxyCircuitBreakerEnabled(true)
            .forwardProxyCircuitBreakerFailureThreshold(1)
            .forwardProxyCircuitBreakerWindowMillis(1_000L);
        java.util.logging.Logger julLogger = java.util.logging.Logger.getLogger(ForwardCircuitBreaker.class.getName());
        List<String> captured = new ArrayList<>();
        java.util.logging.Handler handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                String message = String.valueOf(record.getMessage());
                if (message.contains(marker)) {
                    synchronized (captured) {
                        captured.add(message);
                    }
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        java.util.logging.Level originalLevel = julLogger.getLevel();
        julLogger.addHandler(handler);
        julLogger.setLevel(java.util.logging.Level.ALL);
        try {
            // opened
            breaker.recordFailure(tripOnFirstFailure, key);
            // re-opened after a failed half-open trial
            clock.addAndGet(1_000L);
            assertThat(breaker.allowRequest(tripOnFirstFailure, key), is(true));
            breaker.recordFailure(tripOnFirstFailure, key);
            // closed after a successful half-open trial
            clock.addAndGet(1_000L);
            assertThat(breaker.allowRequest(tripOnFirstFailure, key), is(true));
            breaker.recordSuccess(tripOnFirstFailure, key);
        } finally {
            julLogger.removeHandler(handler);
            julLogger.setLevel(originalLevel);
        }

        assertThat(captured, hasSize(3));
        for (String message : captured) {
            assertThat(message, not(containsString("\r")));
            assertThat(message, not(containsString("\n")));
            assertThat(message, containsString("evil\\r\\n2026-01-01 INFO " + marker + ":80"));
        }
    }
}
