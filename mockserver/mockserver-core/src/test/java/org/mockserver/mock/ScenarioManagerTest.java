package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class ScenarioManagerTest {

    private ScenarioManager scenarioManager;

    @Before
    public void setUp() {
        scenarioManager = new ScenarioManager();
    }

    @Test
    public void shouldReturnStartedAsDefaultState() {
        assertThat(scenarioManager.getState("nonExistentScenario"), is(ScenarioManager.STARTED));
    }

    @Test
    public void shouldSetAndGetState() {
        // when
        scenarioManager.setState("myScenario", "Step1");

        // then
        assertThat(scenarioManager.getState("myScenario"), is("Step1"));
    }

    @Test
    public void shouldMatchStateWhenScenarioNameIsNull() {
        assertThat(scenarioManager.matchesState(null, "Started"), is(true));
    }

    @Test
    public void shouldMatchStateWhenRequiredStateIsNull() {
        assertThat(scenarioManager.matchesState("myScenario", null), is(true));
    }

    @Test
    public void shouldMatchStateWhenBothAreNull() {
        assertThat(scenarioManager.matchesState(null, null), is(true));
    }

    @Test
    public void shouldMatchStateWhenStateEquals() {
        assertThat(scenarioManager.matchesState("myScenario", "Started"), is(true));
    }

    @Test
    public void shouldNotMatchStateWhenStateDiffers() {
        assertThat(scenarioManager.matchesState("myScenario", "Step1"), is(false));
    }

    @Test
    public void shouldMatchStateAfterTransition() {
        // when
        scenarioManager.transitionState("myScenario", "Step1");

        // then
        assertThat(scenarioManager.matchesState("myScenario", "Step1"), is(true));
        assertThat(scenarioManager.matchesState("myScenario", "Started"), is(false));
    }

    @Test
    public void shouldNotTransitionWhenScenarioNameIsNull() {
        // when
        scenarioManager.transitionState(null, "Step1");

        // then - no exception, no state change
        assertThat(scenarioManager.getAllStates().isEmpty(), is(true));
    }

    @Test
    public void shouldNotTransitionWhenNewStateIsNull() {
        // when
        scenarioManager.transitionState("myScenario", null);

        // then - no state stored
        assertThat(scenarioManager.getState("myScenario"), is(ScenarioManager.STARTED));
    }

    @Test
    public void shouldClearScenario() {
        // given
        scenarioManager.setState("myScenario", "Step1");

        // when
        scenarioManager.clear("myScenario");

        // then
        assertThat(scenarioManager.getState("myScenario"), is(ScenarioManager.STARTED));
    }

    @Test
    public void shouldClearHandleNull() {
        // when - should not throw
        scenarioManager.clear(null);
    }

    @Test
    public void shouldResetAllStates() {
        // given
        scenarioManager.setState("scenario1", "Step1");
        scenarioManager.setState("scenario2", "Step2");

        // when
        scenarioManager.reset();

        // then
        assertThat(scenarioManager.getState("scenario1"), is(ScenarioManager.STARTED));
        assertThat(scenarioManager.getState("scenario2"), is(ScenarioManager.STARTED));
        assertThat(scenarioManager.getAllStates().isEmpty(), is(true));
    }

    @Test
    public void shouldReturnAllStates() {
        // given
        scenarioManager.setState("scenario1", "Step1");
        scenarioManager.setState("scenario2", "Step2");

        // when
        Map<String, String> allStates = scenarioManager.getAllStates();

        // then
        assertThat(allStates.size(), is(2));
        assertThat(allStates.get("scenario1"), is("Step1"));
        assertThat(allStates.get("scenario2"), is("Step2"));
    }

    @Test
    public void shouldReturnDefensiveCopyOfStates() {
        // given
        scenarioManager.setState("scenario1", "Step1");

        // when
        Map<String, String> allStates = scenarioManager.getAllStates();
        allStates.put("scenario2", "Step2");

        // then - original should not be modified
        assertThat(scenarioManager.getAllStates().size(), is(1));
    }

    @Test
    public void shouldMatchAndTransitionAtomically() {
        assertThat(scenarioManager.matchesAndTransition("myScenario", "Started", "Step1"), is(true));
        assertThat(scenarioManager.getState("myScenario"), is("Step1"));
    }

    @Test
    public void shouldNotMatchAndTransitionWhenStateDiffers() {
        assertThat(scenarioManager.matchesAndTransition("myScenario", "Step1", "Step2"), is(false));
        assertThat(scenarioManager.getState("myScenario"), is(ScenarioManager.STARTED));
    }

    @Test
    public void shouldMatchAndTransitionWithNullNewState() {
        assertThat(scenarioManager.matchesAndTransition("myScenario", "Started", null), is(true));
        assertThat(scenarioManager.getState("myScenario"), is(ScenarioManager.STARTED));
    }

    @Test
    public void shouldMatchAndTransitionWithNullScenarioName() {
        assertThat(scenarioManager.matchesAndTransition(null, "Started", "Step1"), is(true));
    }

    @Test
    public void shouldMatchAndTransitionWithNullRequiredState() {
        assertThat(scenarioManager.matchesAndTransition("myScenario", null, "Step1"), is(true));
    }

    @Test
    public void shouldGetStateReturnStartedForNull() {
        assertThat(scenarioManager.getState(null), is(ScenarioManager.STARTED));
    }

    @Test
    public void shouldSetStateIgnoreNullScenarioName() {
        scenarioManager.setState(null, "Step1");
        assertThat(scenarioManager.getAllStates().isEmpty(), is(true));
    }

    @Test
    public void shouldSetStateIgnoreNullState() {
        scenarioManager.setState("myScenario", null);
        assertThat(scenarioManager.getAllStates().isEmpty(), is(true));
    }

    @Test
    public void shouldHandleConcurrentMatchesAndTransition() throws Exception {
        int threadCount = 10;
        java.util.concurrent.CyclicBarrier barrier = new java.util.concurrent.CyclicBarrier(threadCount);
        java.util.concurrent.atomic.AtomicInteger successCount = new java.util.concurrent.atomic.AtomicInteger(0);
        Thread[] threads = new Thread[threadCount];

        for (int i = 0; i < threadCount; i++) {
            threads[i] = new Thread(() -> {
                try {
                    barrier.await();
                    if (scenarioManager.matchesAndTransition("concurrentScenario", "Started", "Step1")) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
            threads[i].start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(successCount.get(), is(1));
        assertThat(scenarioManager.getState("concurrentScenario"), is("Step1"));
    }
}
