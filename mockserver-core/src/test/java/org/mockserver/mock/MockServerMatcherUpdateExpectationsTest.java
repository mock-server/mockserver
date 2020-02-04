package org.mockserver.mock;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.closurecallback.websocketregistry.WebSocketClientRegistry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.scheduler.Scheduler;
import org.mockserver.ui.MockServerMatcherNotifier;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.mock;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class MockServerMatcherUpdateExpectationsTest {

    private MockServerMatcher mockServerMatcher;

    @Before
    public void prepareTestFixture() {
        Scheduler scheduler = mock(Scheduler.class);
        WebSocketClientRegistry webSocketClientRegistry = mock(WebSocketClientRegistry.class);
        mockServerMatcher = new MockServerMatcher(new MockServerLogger(), scheduler, webSocketClientRegistry);
    }

    @Test
    public void shouldUpdateExistingExpectation() {
        // given
        String key = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("somePath")).withId(key).thenRespond(response().withBody("someBody")));

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), nullValue());
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));

        // when
        Expectation expectation = new Expectation(request().withPath("someOtherPath")).withId(key).thenRespond(response().withBody("someBody"));
        mockServerMatcher.add(expectation);

        // then
        assertThat(mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("someOtherPath")), is(expectation));
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(1));
    }

    @Test
    public void shouldUpdateAllExpectationAndHandleNull() {
        // given
        String keyOne = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")));
        String keyTwo = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")));
        String keyThree = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));

        // when
        mockServerMatcher.update(
            null,
            MockServerMatcherNotifier.Cause.API
        );

        // then
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            is(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            is(new Expectation(request().withPath("path_two")).withId(keyOne).thenRespond(response().withBody("body_two")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            is(new Expectation(request().withPath("path_three")).withId(keyOne).thenRespond(response().withBody("body_three")))
        );
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));
    }

    @Test
    public void shouldUpdateAllExpectationNoneNewNoneRemoved() {
        // given
        String keyOne = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")));
        String keyTwo = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")));
        String keyThree = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));

        // when
        mockServerMatcher.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three"))
            },
            MockServerMatcherNotifier.Cause.API
        );

        // then
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("new_path_one")),
            is(new Expectation(request().withPath("new_path_one")).thenRespond(response().withBody("new_body_one")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            is(new Expectation(request().withPath("path_two")).thenRespond(response().withBody("body_two")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("new_path_three")),
            is(new Expectation(request().withPath("new_path_three")).thenRespond(response().withBody("new_body_three")))
        );
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));
    }

    @Test
    public void shouldUpdateAllExpectationNoneExistingNoneRemoved() {
        // given
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));

        // when
        mockServerMatcher.update(
            new Expectation[]{
                new Expectation(request().withPath("path_one")).thenRespond(response().withBody("body_one")),
                new Expectation(request().withPath("path_two")).thenRespond(response().withBody("body_two")),
                new Expectation(request().withPath("path_three")).thenRespond(response().withBody("body_three"))
            },
            MockServerMatcherNotifier.Cause.API
        );

        // then
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            is(new Expectation(request().withPath("path_one")).thenRespond(response().withBody("body_one")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            is(new Expectation(request().withPath("path_two")).thenRespond(response().withBody("body_two")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            is(new Expectation(request().withPath("path_three")).thenRespond(response().withBody("body_three")))
        );
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));
    }

    @Test
    public void shouldUpdateAllExpectationNoneNewNoneExisting() {
        // given
        String keyOne = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")));
        String keyTwo = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")));
        String keyThree = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));

        // when
        mockServerMatcher.update(
            new Expectation[]{

            },
            MockServerMatcherNotifier.Cause.API
        );

        // then
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(0));
    }

    @Test
    public void shouldUpdateAllExpectationWithExistingAndRemoved() {
        // given
        String keyOne = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")));
        String keyTwo = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")));
        String keyThree = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")));

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));

        // when
        mockServerMatcher.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three"))
            },
            MockServerMatcherNotifier.Cause.API
        );

        // then
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("new_path_one")),
            is(new Expectation(request().withPath("new_path_one")).thenRespond(response().withBody("new_body_one")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("new_path_three")),
            is(new Expectation(request().withPath("new_path_three")).thenRespond(response().withBody("new_body_three")))
        );
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(2));
    }

    @Test
    public void shouldUpdateAllExpectationWithNewExistingAndRemoved() {
        // given
        String keyOne = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_one")).withId(keyOne).thenRespond(response().withBody("body_one")));
        String keyTwo = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_two")).withId(keyTwo).thenRespond(response().withBody("body_two")));
        String keyThree = UUID.randomUUID().toString();
        mockServerMatcher.add(new Expectation(request().withPath("path_three")).withId(keyThree).thenRespond(response().withBody("body_three")));
        String keyFour = UUID.randomUUID().toString();

        // then
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));

        // when
        mockServerMatcher.update(
            new Expectation[]{
                new Expectation(request().withPath("new_path_one")).withId(keyOne).thenRespond(response().withBody("new_body_one")),
                new Expectation(request().withPath("new_path_three")).withId(keyThree).thenRespond(response().withBody("new_body_three")),
                new Expectation(request().withPath("path_four")).withId(keyFour).thenRespond(response().withBody("body_four"))
            },
            MockServerMatcherNotifier.Cause.API
        );

        // then
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_one")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("new_path_one")),
            is(new Expectation(request().withPath("new_path_one")).thenRespond(response().withBody("new_body_one")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_two")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_three")),
            nullValue()
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("new_path_three")),
            is(new Expectation(request().withPath("new_path_three")).thenRespond(response().withBody("new_body_three")))
        );
        assertThat(
            mockServerMatcher.firstMatchingExpectation(new HttpRequest().withPath("path_four")),
            is(new Expectation(request().withPath("path_four")).thenRespond(response().withBody("body_four")))
        );
        assertThat(mockServerMatcher.httpRequestMatchers.size(), is(3));
    }

}
