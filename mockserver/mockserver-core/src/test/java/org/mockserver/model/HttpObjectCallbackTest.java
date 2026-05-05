package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpObjectCallbackTest {

    @Test
    public void returnsCallbackClass() {
        assertEquals("some_client_id", new HttpObjectCallback().withClientId("some_client_id").getClientId());
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                "  \"clientId\" : \"some_client_id\"" + NEW_LINE +
                "}",
            new HttpObjectCallback()
                .withClientId("some_client_id")
                .toString()
        );
    }
}
