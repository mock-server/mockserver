package org.mockserver.mock;

import org.junit.Test;
import org.mockserver.model.Action;
import org.mockserver.model.HttpForwardValidateAction;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;
import static org.mockserver.model.HttpForwardValidateAction.forwardValidate;
import static org.mockserver.model.HttpRequest.request;

public class ExpectationForwardValidateTest {

    @Test
    public void shouldSetAndGetForwardValidateAction() {
        HttpForwardValidateAction action = forwardValidate()
            .withSpecUrlOrPayload("spec.json")
            .withHost("localhost")
            .withPort(8080);

        Expectation expectation = Expectation.when(request("/test"))
            .thenForwardValidate(action);

        assertThat(expectation.getHttpForwardValidateAction(), is(action));
        assertNull(expectation.getHttpResponse());
        assertNull(expectation.getHttpForward());
        assertNull(expectation.getHttpOverrideForwardedRequest());
    }

    @Test
    public void shouldReturnForwardValidateAsAction() {
        HttpForwardValidateAction action = forwardValidate()
            .withSpecUrlOrPayload("spec.json")
            .withHost("localhost");

        Expectation expectation = Expectation.when(request("/test"))
            .thenForwardValidate(action);

        Action<?> retrieved = expectation.getAction();
        assertNotNull(retrieved);
        assertThat(retrieved.getType(), is(Action.Type.FORWARD_VALIDATE));
        assertTrue(retrieved instanceof HttpForwardValidateAction);
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowResponseAndForwardValidate() {
        Expectation.when(request("/test"))
            .thenRespond(org.mockserver.model.HttpResponse.response().withStatusCode(200))
            .thenForwardValidate(forwardValidate().withSpecUrlOrPayload("spec.json").withHost("localhost"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldNotAllowForwardValidateAndResponse() {
        Expectation.when(request("/test"))
            .thenForwardValidate(forwardValidate().withSpecUrlOrPayload("spec.json").withHost("localhost"))
            .thenRespond(org.mockserver.model.HttpResponse.response().withStatusCode(200));
    }

    @Test
    public void shouldCloneExpectationWithForwardValidate() {
        HttpForwardValidateAction action = forwardValidate()
            .withSpecUrlOrPayload("spec.json")
            .withHost("localhost")
            .withPort(8080);

        Expectation original = Expectation.when(request("/test"))
            .thenForwardValidate(action);

        Expectation cloned = original.clone();

        assertThat(cloned.getHttpForwardValidateAction(), is(action));
        assertEquals(original, cloned);
    }

    @Test
    public void shouldIgnoreNullForwardValidateAction() {
        Expectation expectation = Expectation.when(request("/test"))
            .thenForwardValidate(null);

        assertNull(expectation.getHttpForwardValidateAction());
        assertNull(expectation.getAction());
    }
}
