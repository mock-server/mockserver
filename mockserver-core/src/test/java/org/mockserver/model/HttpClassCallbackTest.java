package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;
import org.mockserver.mock.action.ExpectationForwardCallback;
import org.mockserver.mock.action.ExpectationResponseCallback;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpClassCallback.callback;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackTest {

    @Test
    @SuppressWarnings("AccessStaticViaInstance")
    public void shouldAlwaysCreateNewObject() {
        assertEquals(callback(), callback());
        assertNotSame(callback(), callback());
    }

    @Test
    public void returnsCallbackClass() {
        assertEquals("some_class", new HttpClassCallback().withCallbackClass("some_class").getCallbackClass());
        assertEquals("some_class", callback().withCallbackClass("some_class").getCallbackClass());
        assertEquals("some_class", callback("some_class").getCallbackClass());
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                        "  \"callbackClass\" : \"some_class\"" + NEW_LINE +
                        "}",
                callback()
                        .withCallbackClass("some_class")
                        .toString()
        );
    }

    @Test
    public void shouldHandleValidExpectationCallbacks() {
        assertEquals("org.mockserver.model.HttpClassCallbackTest$TestResponseCallback", 
            new HttpClassCallback().withCallbackClass(TestResponseCallback.class).getCallbackClass());
        assertEquals("org.mockserver.model.HttpClassCallbackTest$TestForwardCallback", 
            new HttpClassCallback().withCallbackClass(TestForwardCallback.class).getCallbackClass());
    }

    public static class TestResponseCallback implements ExpectationResponseCallback {
        @Override
        public HttpResponse handle(HttpRequest httpRequest) {
            return null;
        }
    }

    public static class TestForwardCallback implements ExpectationForwardCallback {
        @Override
        public HttpRequest handle(HttpRequest httpRequest) {
            return null;
        }
    }
}
