package org.mockserver.filters;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;
import static org.mockserver.verify.VerificationTimes.atLeast;
import static org.mockserver.verify.VerificationTimes.exactly;

/**
 * @author jamesdbloom
 */
public class RequestLogFilterVerificationTest {

    @Test
    public void shouldPassVerificationWithNullRequest() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify((Verification) null), is(""));
    }

    @Test
    public void shouldPassVerificationWithDefaultTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                ),
                is(""));
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationWithAtLeastTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest().withPath("some_path")
                                )
                                .withTimes(atLeast(2))
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationWithAtLeastZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest().withPath("some_non_matching_path")
                                )
                                .withTimes(atLeast(0))
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationWithExactlyTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_path")
                                )
                                .withTimes(exactly(2))
                ),
                is(""));
    }

    @Test
    public void shouldPassVerificationWithExactlyZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_non_matching_path")
                                )
                                .withTimes(exactly(0))
                ),
                is(""));
    }

    @Test
    public void shouldFailVerificationWithNullRequest() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // then
        assertThat(requestLogFilter.verify((Verification) null), is(""));
    }

    @Test
    public void shouldFailVerificationWithDefaultTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest().withPath("some_non_matching_path")
                                )
                ),
                is("Request not found at least once, expected:<{" + NEW_LINE +
                        "  \"path\" : \"some_non_matching_path\"" + NEW_LINE +
                        "}> but was:<[ {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithAtLeastTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest().withPath("some_other_path")
                                )
                                .withTimes(atLeast(2))
                ),
                is("Request not found at least 2 times, expected:<{" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}> but was:<[ {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithExactTwoTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                                .withTimes(exactly(2))
                ),
                is("Request not found exactly 2 times, expected:<{" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}> but was:<[ {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithExactOneTime() {
        // given
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                                .withTimes(exactly(1))
                ),
                is("Request not found exactly once, expected:<{" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}> but was:<>"));
    }

    @Test
    public void shouldFailVerificationWithExactZeroTimes() {
        // given
        HttpRequest httpRequest = new HttpRequest().withPath("some_path");
        HttpRequest otherHttpRequest = new HttpRequest().withPath("some_other_path");
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);
        requestLogFilter.onRequest(otherHttpRequest);
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(
                                        new HttpRequest()
                                                .withPath("some_other_path")
                                )
                                .withTimes(exactly(0))
                ),
                is("Request not found exactly 0 times, expected:<{" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}> but was:<[ {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_other_path\"" + NEW_LINE +
                        "}, {" + NEW_LINE +
                        "  \"path\" : \"some_path\"" + NEW_LINE +
                        "} ]>"));
    }

    @Test
    public void shouldFailVerificationWithNoInteractions() {
        // given
        HttpRequest httpRequest = new HttpRequest();
        RequestLogFilter requestLogFilter = new RequestLogFilter();

        // when
        requestLogFilter.onRequest(httpRequest);

        // then
        assertThat(requestLogFilter.verify(
                        verification()
                                .withRequest(request())
                                .withTimes(exactly(0))
                ),
                is("Request not found exactly 0 times, expected:<{ }> but was:<{ }>"));
    }
}
