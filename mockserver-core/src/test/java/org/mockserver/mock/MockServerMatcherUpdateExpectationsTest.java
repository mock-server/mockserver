package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.uuid.UUIDService;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.ui.MockServerMatcherNotifier.Cause.API;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherUpdateExpectationsTest {

    private RequestMatchers requestMatchers;

    @Before
    public void prepareTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        requestMatchers = new RequestMatchers(new MockServerLogger(), scheduler, webSocketClientRegistry);
    }

    @Test
    public void shouldUpdateExistingExpectation() {
        // given
        String key = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("somePath")).withId(key).thenRespond(response().withBody("someBody")), API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));

        // when
        Expectation expectation = new Expectation(request().withPath("someOtherPath")).withId(key).thenRespond(response().withBody("someBody"));
        requestMatchers.add(expectation, API);

        // then
        assertThat(requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), is(expectation));
        assertThat(requestMatchers.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldUpdateAllExpectationAndHandleNull() {
        // given
        String keyOne = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")), API);
        String keyTwo = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")), API);
        String keyThree = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")), API);

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));

        // when
        requestMatchers.update(
            null,
            API
        );

        // then
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            is(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            is(new Expectation(request().withPath("path_two")).withId(keyOne).thenRespond(response().withBody("body_two")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            is(new Expectation(request().withPath("path_three")).withId(keyOne).thenRespond(response().withBody("body_three")))
        );
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));
    }

    @Test
    public void shouldUpdateAllExpectationNoneNewNoneRemoved() {
        // given
        String keyOne = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")), API);
        String keyTwo = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")), API);
        String keyThree = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")), API);

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));

        // when
        requestMatchers.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three"))
            },
            API
        );

        // then
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("new_path_one")),
            is(new Expectation(request().withPath("new_path_one")).thenRespond(response().withBody("new_body_one")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            is(new Expectation(request().withPath("path_two")).thenRespond(response().withBody("body_two")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("new_path_three")),
            is(new Expectation(request().withPath("new_path_three")).thenRespond(response().withBody("new_body_three")))
        );
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));
    }

    @Test
    public void shouldUpdateAllExpectationNoneExistingNoneRemoved() {
        // given
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));

        // when
        requestMatchers.update(
            new Expectation[]{
                new Expectation(request().withPath("path_one")).thenRespond(response().withBody("body_one")),
                new Expectation(request().withPath("path_two")).thenRespond(response().withBody("body_two")),
                new Expectation(request().withPath("path_three")).thenRespond(response().withBody("body_three"))
            },
            API
        );

        // then
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            is(new Expectation(request().withPath("path_one")).thenRespond(response().withBody("body_one")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            is(new Expectation(request().withPath("path_two")).thenRespond(response().withBody("body_two")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            is(new Expectation(request().withPath("path_three")).thenRespond(response().withBody("body_three")))
        );
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));
    }

    @Test
    public void shouldUpdateAllExpectationNoneNewNoneExisting() {
        // given
        String keyOne = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")), API);
        String keyTwo = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")), API);
        String keyThree = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")), API);

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));

        // when
        requestMatchers.update(
            new Expectation[]{

            },
            API
        );

        // then
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(requestMatchers.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldUpdateAllExpectationWithExistingAndRemoved() {
        // given
        String keyOne = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")), API);
        String keyTwo = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")), API);
        String keyThree = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")), API);

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));

        // when
        requestMatchers.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three"))
            },
            API
        );

        // then
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("new_path_one")),
            is(new Expectation(request().withPath("new_path_one")).thenRespond(response().withBody("new_body_one")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("new_path_three")),
            is(new Expectation(request().withPath("new_path_three")).thenRespond(response().withBody("new_body_three")))
        );
        assertThat(requestMatchers.httpRequestMatchers.size(), is(2));
    }

    @Test
    public void shouldUpdateAllExpectationWithNewExistingAndRemoved() {
        // given
        String keyOne = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")), API);
        String keyTwo = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")), API);
        String keyThree = UUIDService.getUUID();
        requestMatchers.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")), API);
        String keyFour = UUIDService.getUUID();

        // then
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));

        // when
        requestMatchers.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three")),
                new Expectation(request().withPath("path_four")).withId(keyFour).thenRespond(response().withBody("body_four"))
            },
            API
        );

        // then
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("new_path_one")),
            is(new Expectation(request().withPath("new_path_one")).thenRespond(response().withBody("new_body_one")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("new_path_three")),
            is(new Expectation(request().withPath("new_path_three")).thenRespond(response().withBody("new_body_three")))
        );
        assertThat(
            requestMatchers.firstMatchingExpectation(new HttpRequest().withPath("path_four")),
            is(new Expectation(request().withPath("path_four")).thenRespond(response().withBody("body_four")))
        );
        assertThat(requestMatchers.httpRequestMatchers.size(), is(3));
    }

}
