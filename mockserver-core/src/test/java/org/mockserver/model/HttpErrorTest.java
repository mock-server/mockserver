package org.mockserver.model;

import junit.framework.TestCase;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockserver.model.HttpError.error;

/**
 * @author jamesdbloom
 */
public class HttpErrorTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpError().error(), error());
        assertNotSame(error(), error());
    }

    @Test
    public void returnsDelay() {
        assertEquals(new Delay(TimeUnit.DAYS, 10), new HttpError().withDelay(TimeUnit.DAYS, 10).getDelay());
    }

    @Test
    public void returnsDropConnection() {
        assertEquals(true, new HttpError().withDropConnection(true).getDropConnection());
    }

    @Test
    public void returnsResponseBytes() {
        assertArrayEquals("some_bytes".getBytes(), new HttpError().withResponseBytes("some_bytes".getBytes()).getResponseBytes());
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        TestCase.assertEquals("{" + System.getProperty("line.separator") +
                        "  \"delay\" : {" + System.getProperty("line.separator") +
                        "    \"timeUnit\" : \"DAYS\"," + System.getProperty("line.separator") +
                        "    \"value\" : 10" + System.getProperty("line.separator") +
                        "  }," + System.getProperty("line.separator") +
                        "  \"dropConnection\" : true," + System.getProperty("line.separator") +
                        "  \"responseBytes\" : \"c29tZV9ieXRlcw==\"" + System.getProperty("line.separator") +
                        "}",
                error()
                        .withDelay(TimeUnit.DAYS, 10)
                        .withDropConnection(true)
                        .withResponseBytes("some_bytes".getBytes())
                        .toString()
        );
    }
}
