package org.mockserver.mock;

import org.junit.*;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.ui.MockServerMatcherNotifier.Cause.API;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherNotificationAndMetricsTest {

    private static MockServerLogger mockServerLogger;
    private static Scheduler scheduler;
    private RequestMatchers requestMatchers;

    @BeforeClass
    public static void createScheduler() {
        mockServerLogger = new MockServerLogger();
        scheduler = new Scheduler(mockServerLogger);
        ConfigurationProperties.metricsEnabled(true);
    }

    @Before
    public void createMatcher() {
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(mockServerLogger, scheduler, webSocketClientRegistry);
        Metrics.clear();
    }

    @AfterClass
    public static void shutdownScheduler() {
        scheduler.shutdown();
        ConfigurationProperties.metricsEnabled(false);
    }

    @Test
    public void shouldNotifyOnAdd() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        requestMatchers.registerListener((requestMatchers, cause) -> {
            causes.add(cause);
        });

        // when
        requestMatchers.add(new Expectation(
                    request()
                        .withPath("somePath")
                ).thenRespond(
                    response()
                        .withBody("someBody")
                ), API);

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(causes, contains(API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
    }

    @Test
    public void shouldNotifyOnRemove() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        requestMatchers.registerListener((requestMatchers, cause) -> {
            causes.add(cause);
        });
        requestMatchers.add(new Expectation(
                    request()
                        .withPath("somePath")
                ).thenForward(
                    forward()
                ), API);
        requestMatchers.add(new Expectation(
                    request()
                        .withPath("somePath")
                ).thenRespond(
                    response()
                        .withBody("someBody")
                ), API);

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(2));
        assertThat(causes, contains(API, API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
        assertThat(Metrics.get(Metrics.Name.ACTION_FORWARD_COUNT), is(1));

        // when
        causes.clear();
        requestMatchers.reset();

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
        assertThat(causes, contains(API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(0));
        assertThat(Metrics.get(Metrics.Name.ACTION_FORWARD_COUNT), is(0));
    }

    @Test
    public void shouldNotifyOnUpdate() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        requestMatchers.registerListener((requestMatchers, cause) -> {
            causes.add(cause);
        });
        requestMatchers.add(new Expectation(
                    request()
                        .withPath("somePath")
                ).withId("one").thenRespond(
                    response()
                        .withBody("someBody")
                ), API);

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(causes, contains(API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));

        // when
        causes.clear();
        requestMatchers.add(new Expectation(
                    request()
                        .withPath("someOtherPath")
                ).withId("one").thenRespond(
                    response()
                        .withBody("someOtherBody")
                ), API);

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
        assertThat(causes, contains(API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
    }



    @Test
    public void shouldUpdateAllExpectationWithNewExistingAndRemoved() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        requestMatchers.registerListener((requestMatchers, cause) -> {
            causes.add(cause);
        });
        String keyOne = UUID.randomUUID().toString();
        requestMatchers.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")), API);
        String keyTwo = UUID.randomUUID().toString();
        requestMatchers.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenForward(new HttpObjectCallback(){

        }), API);
        String keyThree = UUID.randomUUID().toString();
        requestMatchers.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(new HttpObjectCallback(){

        }), API);
        String keyFour = UUID.randomUUID().toString();

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));
        assertThat(causes, contains(
            API,
            API,
            API
        ));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_OBJECT_CALLBACK_COUNT), is(1));
        assertThat(Metrics.get(Metrics.Name.ACTION_FORWARD_OBJECT_CALLBACK_COUNT), is(1));

        // when
        causes.clear();
        requestMatchers.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three")),
                new Expectation(request().withPath("path_four")).withId(keyFour).thenRespond(response().withBody("body_four"))
            },
            API
        );

        // then
        MILLISECONDS.sleep(500);
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));
        assertThat(causes, contains(API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(3));
    }

}
