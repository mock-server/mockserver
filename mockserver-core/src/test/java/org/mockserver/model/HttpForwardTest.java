package org.mockserver.model;

import junit.framework.TestCase;
import org.json.JSONException;
import org.junit.Test;
import org.skyscreamer.jsonassert.JSONAssert;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpForward.forward;

/**
 * @author jamesdbloom
 */
@SuppressWarnings({"RedundantSuppression", "deprecation"})
public class HttpForwardTest {

    public void assertJsonEqualsNonStrict(String json1, String json2) {
        try {
            JSONAssert.assertEquals(json1, json2, false);
        } catch (JSONException jse) {
            throw new IllegalArgumentException(jse.getMessage());
        }
    }

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(forward(), forward());
        assertNotSame(forward(), forward());
    }

    @Test
    public void returnsPort() {
        assertEquals(new Integer(9090), new HttpForward().withPort(9090).getPort());
    }

    @Test
    public void returnsHost() {
        assertEquals("some_host", new HttpForward().withHost("some_host").getHost());
    }

    @Test
    public void returnsDelay() {
        assertEquals(new Delay(TimeUnit.HOURS, 1), new HttpForward().withDelay(new Delay(TimeUnit.HOURS, 1)).getDelay());
        assertEquals(new Delay(TimeUnit.HOURS, 1), new HttpForward().withDelay(TimeUnit.HOURS, 1).getDelay());
    }

    @Test
    public void returnsScheme() {
        assertEquals(HttpForward.Scheme.HTTPS, new HttpForward().withScheme(HttpForward.Scheme.HTTPS).getScheme());
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        assertJsonEqualsNonStrict("{" + NEW_LINE +
                "  \"delay\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"HOURS\"," + NEW_LINE +
                "    \"value\" : 1" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"host\" : \"some_host\"," + NEW_LINE +
                "  \"port\" : 9090," + NEW_LINE +
                "  \"scheme\" : \"HTTPS\"" + NEW_LINE +
                "}",
            forward()
                .withHost("some_host")
                .withPort(9090)
                .withScheme(HttpForward.Scheme.HTTPS)
                .withDelay(TimeUnit.HOURS, 1)
                .toString()
        );
    }
}
