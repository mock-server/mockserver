package org.mockserver.validator;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.HttpObjectCallback;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.text.IsEmptyString.isEmptyString;
import static org.junit.Assert.assertThat;
import static org.mockserver.model.HttpClassCallback.callback;
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
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, is("2 errors:\n - no request matcher\n - no response, forward, callback or error\n"));
    }

    @Test
    public void shouldValidateRequestMissing() {
        // given
        Expectation expectation = new Expectation(null, Times.once(), TimeToLive.unlimited()).thenRespond(response());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, is("1 errors:\n - no request matcher\n"));
    }

    @Test
    public void shouldValidateAllResultFieldsMissing() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, is("1 errors:\n - no response, forward, callback or error\n"));
    }

    @Test
    public void shouldValidateNoErrorsWithResponse() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenRespond(response());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, isEmptyString());
    }

    @Test
    public void shouldValidateNoErrorsWithClassCallback() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenCallback(callback());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, isEmptyString());
    }

    @Test
    public void shouldValidateNoErrorsWithObjectCallback() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenCallback(new HttpObjectCallback());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, isEmptyString());
    }

    @Test
    public void shouldValidateNoErrorsWithForward() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenForward(forward());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, isEmptyString());
    }

    @Test
    public void shouldValidateNoErrorsWithError() {
        // given
        Expectation expectation = new Expectation(request(), Times.once(), TimeToLive.unlimited()).thenError(error());

        // when
        String valid = new ExpectationValidator().isValid(expectation);

        // then
        assertThat(valid, isEmptyString());
    }

}