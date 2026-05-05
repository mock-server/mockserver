package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotSame;
import static org.junit.Assert.assertArrayEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpError.error;

/**
 * @author jamesdbloom
 */
public class HttpErrorTest {

    @Test
    @SuppressWarnings("AccessStaticViaInstance")
    public void shouldAlwaysCreateNewObject() {
        assertEquals(error(), error());
        assertNotSame(error(), error());
    }

    @Test
    public void returnsDelay() {
        assertEquals(new Delay(TimeUnit.DAYS, 10), new HttpError().withDelay(TimeUnit.DAYS, 10).getDelay());
    }

    @Test
    public void returnsDropConnection() {
        assertEquals(Boolean.TRUE, new HttpError().withDropConnection(true).getDropConnection());
    }

    @Test
    public void returnsResponseBytes() {
        assertArrayEquals("some_bytes".getBytes(UTF_8), new HttpError().withResponseBytes("some_bytes".getBytes(UTF_8)).getResponseBytes());
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + NEW_LINE +
                "  \"delay\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"DAYS\"," + NEW_LINE +
                "    \"value\" : 10" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"dropConnection\" : true," + NEW_LINE +
                "  \"responseBytes\" : \"c29tZV9ieXRlcw==\"" + NEW_LINE +
                "}",
            error()
                .withDelay(TimeUnit.DAYS, 10)
                .withDropConnection(true)
                .withResponseBytes("some_bytes".getBytes(UTF_8))
                .toString()
        );
    }
}
