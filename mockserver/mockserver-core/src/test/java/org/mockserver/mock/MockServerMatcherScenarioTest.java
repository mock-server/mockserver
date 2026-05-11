package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.mock.listeners.MockServerMatcherNotifier.Cause.API;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerMatcherScenarioTest {

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(configuration(), new MockServerLogger(), scheduler, webSocketClientRegistry);
    }

    @Test
    public void shouldMatchExpectationWithoutScenario() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"))
            .thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then
        assertEquals(expectation, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldMatchExpectationInStartedState() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Started")
            .thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then
        assertEquals(expectation, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldNotMatchExpectationInWrongState() {
        // when
        Expectation expectation = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Step1")
            .thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then - scenario is in "Started" state, not "Step1"
        assertNull(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldTransitionScenarioStateOnMatch() {
        // given
        Expectation step1 = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Started")
            .withNewScenarioState("Step1")
            .thenRespond(response().withBody("response1"));
        requestMatchers.add(step1, API);

        Expectation step2 = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Step1")
            .thenRespond(response().withBody("response2"));
        requestMatchers.add(step2, API);

        // when - first request matches step1 and transitions to Step1
        assertEquals(step1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));

        // then - second request matches step2 because scenario is now in "Step1"
        assertEquals(step2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldSupportMultiStepScenario() {
        // given
        Expectation step1 = new Expectation(request().withPath("somePath"))
            .withScenarioName("checkout")
            .withScenarioState("Started")
            .withNewScenarioState("ItemAdded")
            .thenRespond(response().withBody("item added"));
        requestMatchers.add(step1, API);

        Expectation step2 = new Expectation(request().withPath("somePath"))
            .withScenarioName("checkout")
            .withScenarioState("ItemAdded")
            .withNewScenarioState("PaymentProcessed")
            .thenRespond(response().withBody("payment processed"));
        requestMatchers.add(step2, API);

        Expectation step3 = new Expectation(request().withPath("somePath"))
            .withScenarioName("checkout")
            .withScenarioState("PaymentProcessed")
            .thenRespond(response().withBody("order complete"));
        requestMatchers.add(step3, API);

        // then
        assertEquals(step1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
        assertEquals(step2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
        assertEquals(step3, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldSupportIndependentScenarios() {
        // given
        Expectation scenarioAStep1 = new Expectation(request().withPath("pathA"))
            .withScenarioName("scenarioA")
            .withScenarioState("Started")
            .withNewScenarioState("A_Step1")
            .thenRespond(response().withBody("A response 1"));
        requestMatchers.add(scenarioAStep1, API);

        Expectation scenarioAStep2 = new Expectation(request().withPath("pathA"))
            .withScenarioName("scenarioA")
            .withScenarioState("A_Step1")
            .thenRespond(response().withBody("A response 2"));
        requestMatchers.add(scenarioAStep2, API);

        Expectation scenarioBStep1 = new Expectation(request().withPath("pathB"))
            .withScenarioName("scenarioB")
            .withScenarioState("Started")
            .withNewScenarioState("B_Step1")
            .thenRespond(response().withBody("B response 1"));
        requestMatchers.add(scenarioBStep1, API);

        Expectation scenarioBStep2 = new Expectation(request().withPath("pathB"))
            .withScenarioName("scenarioB")
            .withScenarioState("B_Step1")
            .thenRespond(response().withBody("B response 2"));
        requestMatchers.add(scenarioBStep2, API);

        // then - interleave requests to different scenarios
        assertEquals(scenarioAStep1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("pathA")));
        assertEquals(scenarioBStep1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("pathB")));
        assertEquals(scenarioAStep2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("pathA")));
        assertEquals(scenarioBStep2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("pathB")));
    }

    @Test
    public void shouldResetScenarioState() {
        // given
        Expectation step1 = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Started")
            .withNewScenarioState("Step1")
            .thenRespond(response().withBody("response1"));
        requestMatchers.add(step1, API);

        Expectation step2 = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Step1")
            .thenRespond(response().withBody("response2"));
        requestMatchers.add(step2, API);

        // transition to Step1
        assertEquals(step1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));

        // when - reset
        requestMatchers.reset(API);

        // then - re-add expectations and verify scenario is back to Started
        requestMatchers.add(step1, API);
        requestMatchers.add(step2, API);
        assertEquals(step1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldMatchWithScenarioNameButNoRequiredState() {
        // when - expectation has scenarioName but no scenarioState (no state check needed)
        Expectation expectation = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withNewScenarioState("Step1")
            .thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then - should match regardless of scenario state
        assertEquals(expectation, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldTransitionStateEvenWithoutRequiredState() {
        // given - step1 transitions without requiring a state, but uses Times.exactly(1) to consume itself
        Expectation step1 = new Expectation(request().withPath("somePath"), Times.exactly(1), org.mockserver.matchers.TimeToLive.unlimited(), 0)
            .withScenarioName("myScenario")
            .withNewScenarioState("Step1")
            .thenRespond(response().withBody("response1"));
        requestMatchers.add(step1, API);

        Expectation step2 = new Expectation(request().withPath("somePath"))
            .withScenarioName("myScenario")
            .withScenarioState("Step1")
            .thenRespond(response().withBody("response2"));
        requestMatchers.add(step2, API);

        // when - first request matches step1 (no state required) and transitions to Step1
        assertEquals(step1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));

        // then - second request matches step2 because scenario is now in "Step1" and step1 is consumed
        assertEquals(step2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldWorkWithTimesAndScenario() {
        // given
        Expectation step1 = new Expectation(request().withPath("somePath"), Times.exactly(1), org.mockserver.matchers.TimeToLive.unlimited(), 0)
            .withScenarioName("myScenario")
            .withScenarioState("Started")
            .withNewScenarioState("Step1")
            .thenRespond(response().withBody("response1"));
        requestMatchers.add(step1, API);

        Expectation step2 = new Expectation(request().withPath("somePath"), Times.unlimited(), org.mockserver.matchers.TimeToLive.unlimited(), 0)
            .withScenarioName("myScenario")
            .withScenarioState("Step1")
            .thenRespond(response().withBody("response2"));
        requestMatchers.add(step2, API);

        // when
        assertEquals(step1, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));

        // then - step1 is consumed (times=1), step2 matches
        assertEquals(step2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
        assertEquals(step2, requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("somePath")));
    }

    @Test
    public void shouldExposeScenarioManager() {
        assertThat(requestMatchers.getScenarioManager().getState("anything"), is(ScenarioManager.STARTED));
    }
}
