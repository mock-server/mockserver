package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpClassCallback.callback;

/**
 * @author jamesdbloom
 */
public class HttpClassCallbackTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpClassCallback().callback(), callback());
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
}
