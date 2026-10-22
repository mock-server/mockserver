package org.mockserver.inprocess;

import org.junit.Test;
import org.mockserver.mock.Expectation;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.verify.VerificationTimes.once;

/**
 * Runs in the sequential surefire phase: reset() also resets the process-wide registries every HttpState shares.
 */
public class InProcessMockServerResetTest {

    @Test
    public void shouldClearExpectationsAndRecordedRequestsOnReset() {
        try (InProcessMockServer server = new InProcessMockServer()) {
            server.upsert(new Expectation(request().withPath("/reset")).thenRespond(response().withBody("before")));
            assertThat(server.handle(request().withPath("/reset")).getStatusCode(), is(200));

            server.reset();

            assertThat(server.handle(request().withPath("/reset")).getStatusCode(), is(404));
            server.verify(request().withPath("/reset"), once());
            assertThat(server.retrieveRecordedRequests(null).length, is(1));
        }
    }
}
