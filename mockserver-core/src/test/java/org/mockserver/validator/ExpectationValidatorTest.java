package org.mockserver.validator;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpCallback;
import org.mockserver.model.HttpRequest;

import java.util.List;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.emptyIterableOf;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpCallback.callback;
import static org.mockserver.model.HttpError.error;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class ExpectationValidatorTest {

    @Test
    public void shouldValidateAllRequiredFieldsMissing() {
        // given
        Expectation expectation = new Expectation(null, Times.once(), TimeToLive.unlimited());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, contains("no request matcher", "no response, forward, callback or error"));
    }

    @Test
    public void shouldValidateRequestMissing() {
        // given
        Expectation expectation = new Expectation(null, Times.once(), TimeToLive.unlimited()).thenRespond(response());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, contains("no request matcher"));
    }

    @Test
    public void shouldValidateAllResultFieldsMissing() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, contains("no response, forward, callback or error"));
    }

    @Test
    public void shouldValidateNoErrorsWithResponse() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenRespond(response());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, emptyIterableOf(String.class));
    }

    @Test
    public void shouldValidateNoErrorsWithCallback() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenCallback(callback());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, emptyIterableOf(String.class));
    }

    @Test
    public void shouldValidateNoErrorsWithForward() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenForward(forward());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, emptyIterableOf(String.class));
    }

    @Test
    public void shouldValidateNoErrorsWithError() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenError(error());

        // when
        List<String> valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, emptyIterableOf(String.class));
    }

}