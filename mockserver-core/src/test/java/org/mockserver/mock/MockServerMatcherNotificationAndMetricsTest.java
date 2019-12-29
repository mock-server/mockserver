package org.mockserver.mock;

import org.junit.*;
import org.mockserver.callback.WebSocketClientRegistry;
import org.mockserver.configuration.ConfigurationProperties;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.metrics.Metrics;
import org.mockserver.model.HttpForward;
import org.mockserver.model.HttpObjectCallback;
import org.mockserver.model.HttpRequest;
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

/**
 * @author jamesdbloom
 */
public class MockServerMatcherNotificationAndMetricsTest {

    private static MockServerLogger mockServerLogger;
    private static Scheduler scheduler;
    private MockServerMatcher mockServerMatcher;

    @BeforeClass
    public static void createScheduler() {
        mockServerLogger = new MockServerLogger();
        scheduler = new Scheduler(mockServerLogger);
        ConfigurationProperties.metricsEnabled(true);
    }

    @Before
    public void createMatcher() {
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerMatcher = new MockServerMatcher(mockServerLogger, scheduler, webSocketClientRegistry);
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
        mockServerMatcher.registerListener((mockServerMatcher, cause) -> {
            causes.add(cause);
        });

        // when
        mockServerMatcher
            .add(
                new Expectation(
                    request()
                        .withPath("somePath")
                ).thenRespond(
                    response()
                        .withBody("someBody")
                )
            );

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(causes, contains(MockServerMatcherNotifier.Cause.API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
    }

    @Test
    public void shouldNotifyOnRemove() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        mockServerMatcher.registerListener((mockServerMatcher, cause) -> {
            causes.add(cause);
        });
        mockServerMatcher
            .add(
                new Expectation(
                    request()
                        .withPath("somePath")
                ).thenForward(
                    forward()
                )
            );
        mockServerMatcher
            .add(
                new Expectation(
                    request()
                        .withPath("somePath")
                ).thenRespond(
                    response()
                        .withBody("someBody")
                )
            );

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(2));
        assertThat(causes, contains(MockServerMatcherNotifier.Cause.API, MockServerMatcherNotifier.Cause.API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
        assertThat(Metrics.get(Metrics.Name.ACTION_FORWARD_COUNT), is(1));

        // when
        causes.clear();
        mockServerMatcher.reset();

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
        assertThat(causes, contains(MockServerMatcherNotifier.Cause.API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(0));
        assertThat(Metrics.get(Metrics.Name.ACTION_FORWARD_COUNT), is(0));
    }

    @Test
    public void shouldNotifyOnUpdate() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        mockServerMatcher.registerListener((mockServerMatcher, cause) -> {
            causes.add(cause);
        });
        mockServerMatcher
            .add(
                new Expectation(
                    request()
                        .withPath("somePath")
                ).withId("one").thenRespond(
                    response()
                        .withBody("someBody")
                )
            );

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(causes, contains(MockServerMatcherNotifier.Cause.API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));

        // when
        causes.clear();
        mockServerMatcher
            .add(
                new Expectation(
                    request()
                        .withPath("someOtherPath")
                ).withId("one").thenRespond(
                    response()
                        .withBody("someOtherBody")
                )
            );

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
        assertThat(causes, contains(MockServerMatcherNotifier.Cause.API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
    }



    @Test
    public void shouldUpdateAllExpectationWithNewExistingAndRemoved() throws InterruptedException {
        // given
        List<MockServerMatcherNotifier.Cause> causes = new ArrayList<>();
        mockServerMatcher.registerListener((mockServerMatcher, cause) -> {
            causes.add(cause);
        });
        String keyOne = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")));
        String keyTwo = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenForward(new HttpObjectCallback(){

        }));
        String keyThree = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(new HttpObjectCallback(){

        }));
        String keyFour = UUID.randomUUID().toString();

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));
        assertThat(causes, contains(
            MockServerMatcherNotifier.Cause.API,
            MockServerMatcherNotifier.Cause.API,
            MockServerMatcherNotifier.Cause.API
        ));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(1));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_OBJECT_CALLBACK_COUNT), is(1));
        assertThat(Metrics.get(Metrics.Name.ACTION_FORWARD_OBJECT_CALLBACK_COUNT), is(1));

        // when
        causes.clear();
        mockServerMatcher.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three")),
                new Expectation(request().withPath("path_four")).withId(keyFour).thenRespond(response().withBody("body_four"))
            },
            MockServerMatcherNotifier.Cause.API
        );

        // then
        MILLISECONDS.sleep(500);
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));
        assertThat(causes, contains(MockServerMatcherNotifier.Cause.API));
        assertThat(Metrics.get(Metrics.Name.ACTION_RESPONSE_COUNT), is(3));
    }

}
