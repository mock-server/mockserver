package org.mockserver.matchers;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class TimesTest {

    @Test
    public void shouldCreateCorrectObjects() {
        // when
        assertThat(Times.unlimited().isUnlimited(), is(true));
        assertThat(Times.once().isUnlimited(), is(false));
        assertThat(Times.once().getRemainingTimes(), is(1));
        assertThat(Times.exactly(5).isUnlimited(), is(false));
        assertThat(Times.exactly(5).getRemainingTimes(), is(5));
    }

    @Test
    public void shouldUpdateCountCorrectly() {
        // given
        Times times = Times.exactly(2);

        // then
        assertThat(times.greaterThenZero(), is(true));
        times.decrement();
        times.decrement();
        assertThat(times.greaterThenZero(), is(false));
    }

    @Test
    public void shouldDecrementAndCheckAtomically() {
        Times times = Times.once();
        assertThat(times.decrementAndCheckGreaterThanZero(), is(true));
        assertThat(times.decrementAndCheckGreaterThanZero(), is(false));
    }

    @Test
    public void shouldDecrementAndCheckAtomicallyForUnlimited() {
        Times times = Times.unlimited();
        assertThat(times.decrementAndCheckGreaterThanZero(), is(true));
        assertThat(times.decrementAndCheckGreaterThanZero(), is(true));
    }

    @Test
    public void shouldOnlyAllowOneThreadToConsumeOnceExpectation() throws InterruptedException {
        Times times = Times.once();
        int threadCount = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                if (times.decrementAndCheckGreaterThanZero()) {
                    successCount.incrementAndGet();
                }
                doneLatch.countDown();
            }).start();
        }

        startLatch.countDown();
        doneLatch.await();

        assertThat(successCount.get(), is(1));
    }
}
