package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

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
        TestCase.assertEquals("{" + System.getProperty("line.separator") +
                        "  \"clientId\" : \"some_client_id\"" + System.getProperty("line.separator") +
                        "}",
                new HttpObjectCallback()
                        .withClientId("some_client_id")
                        .toString()
        );
    }
}
