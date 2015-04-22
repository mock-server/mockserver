package org.mockserver.server;

import com.google.common.util.concurrent.Uninterruptibles;
import org.junit.Test;
import org.mockserver.integration.server.AbstractClientServerSharedClassloadersIntegrationTest;
import org.mockserver.model.Delay;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpStatusCode;

import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public abstract class AbstractClientServerSharedClassloadersAndTestClasspathIntegrationTest extends AbstractClientServerSharedClassloadersIntegrationTest {

    @Test
    public void shouldCallbackToSpecifiedClassInTestClasspath() {
        // given
        TestClasspathTestExpectationCallback.httpRequests.clear();
        TestClasspathTestExpectationCallback.httpResponse = response()
                .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                .withHeaders(
                        header("x-callback", "test_callback_header")
                )
                .withBody("a_callback_response");

        // when
        mockServerClient
                .when(
                        request()
                                .withPath("/callback")
                )
                .callback(
                        callback()
                                .withCallbackClass("org.mockserver.server.TestClasspathTestExpectationCallback")
                );

        // then
        // - in http
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .withPath(calculatePath("callback"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_http"),
                        headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getBody().getValue(), "an_example_body_http");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(0).getPath(), "/callback");

        // - in https
        assertEquals(
                response()
                        .withStatusCode(HttpStatusCode.ACCEPTED_202.code())
                        .withHeaders(
                                header("x-callback", "test_callback_header")
                        )
                        .withBody("a_callback_response"),
                makeRequest(
                        request()
                                .setSecure(true)
                                .withPath(calculatePath("callback"))
                                .withMethod("POST")
                                .withHeaders(
                                        header("X-Test", "test_headers_and_body")
                                )
                                .withBody("an_example_body_https"),
                        headersToIgnore)
        );
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getBody().getValue(), "an_example_body_https");
        assertEquals(TestClasspathTestExpectationCallback.httpRequests.get(1).getPath(), "/callback");
    }

    /**
     * Test demonstrates concurrency issue inside mock server
     *
     * There are two concurrent requests:
     * <ul>
     * <li>"Slow" request (uses {@link Delay}), causes server thread to enter synchronized method
     * {@link org.mockserver.mock.MockServerMatcher#handle(HttpRequest)} and sleep there.</li>
     * <li>"Fast" request, whose server thread fails to enter synchronized method because "slow"
     * thread is sleeping there so its also delayed</li>
     * </ul>
     */
    @Test
    public void testRaceConditionWithDelay() throws Exception {
        mockServerClient.when(request("/slow"))
                        .respond(response("super slow").withDelay(new Delay(TimeUnit.SECONDS, 10)));
        mockServerClient.when(request("/fast"))
                        .respond(response("quite fast"));

        ExecutorService executorService = Executors.newFixedThreadPool(2);

        Future<Long> slowFuture = executorService.submit(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                long start = System.currentTimeMillis();
                makeRequest(request("/slow"), Collections.<String>emptySet());
                return System.currentTimeMillis() - start;
            }
        });

        // Let fast request come to the server slightly after slow request
        Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);

        Future<Long> fastFuture = executorService.submit(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                long start = System.currentTimeMillis();
                makeRequest(request("/fast"), Collections.<String>emptySet());
                return System.currentTimeMillis() - start;

            }
        });

        Long slowRequestElapsedMillis = slowFuture.get();
        Long fastRequestElapsedMillis = fastFuture.get();

        assertThat("Slow request takes less than expected",
                   slowRequestElapsedMillis, is(greaterThan(10 * 1000L)));
        assertThat("Fast request takes longer than expected",
                   fastRequestElapsedMillis, is(lessThan(2 * 1000L)));
    }
}
