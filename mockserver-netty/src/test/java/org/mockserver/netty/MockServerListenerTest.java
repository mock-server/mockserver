package org.mockserver.netty;

import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class MockServerListenerTest {

    @Test
    public void shouldNotifyListener() {
        // given
        List<Expectation> expectations = new ArrayList<>();
        MockServer mockServer = new MockServer().registerListener(expectations::addAll);

        // when
        new MockServerClient("localhost", mockServer.getLocalPort())
            .when(
                request()
                    .withPath("/some/path")
            )
            .respond(
                response()
                    .withBody("some_response_body")
            );

        // then
        assertThat(expectations.size(), is(1));
        assertThat(expectations.get(0), is(new Expectation(
            request()
                .withPath("/some/path"),
            Times.unlimited(),
            TimeToLive.unlimited(),
            0
        ).thenRespond(
            response()
                .withBody("some_response_body")
        )));
    }

}