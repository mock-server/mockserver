package org.mockserver.model;

import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertNull;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpResponseTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpResponse().response(), response());
        assertNotSame(response(), response());
    }

    @Test
    public void returnsResponseCode() {
        assertEquals(new Integer(200), new HttpResponse().withStatusCode(200).getStatusCode());
    }

    @Test
    public void returnsBody() {
        assertEquals(Base64Converter.stringToBase64Bytes("somebody".getBytes()), new HttpResponse().withBody("somebody").getBodyAsString());
        assertArrayEquals("somebody".getBytes(), new HttpResponse().withBody("somebody").getBody());
        assertNull(new HttpResponse().withBody((byte[]) null).getBody());
        assertArrayEquals(new byte[0], new HttpResponse().withBody((String) null).getBody());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaders().get(0));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(Arrays.asList(new Cookie("name", "value"))).getCookies().get(0));
        assertEquals(new Cookie("name", "value"), new HttpRequest().withCookie(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", "value_one", "value_two"), new HttpRequest().withCookie(new Cookie("name", "value_one")).withCookie(new Cookie("name", "value_two")).getCookies().get(0));
    }

    @Test
    public void setsDelay() {
        assertEquals(new Delay(TimeUnit.MILLISECONDS, 10), new HttpResponse().withDelay(new Delay(TimeUnit.MILLISECONDS, 10)).getDelay());
    }

    @Test
    public void appliesDelay() throws InterruptedException {
        // given
        TimeUnit timeUnit = mock(TimeUnit.class);

        // when
        new HttpResponse().withDelay(new Delay(timeUnit, 10)).applyDelay();

        // then
        verify(timeUnit).sleep(10);
    }

    @Test(expected = RuntimeException.class)
    public void applyDelayHandlesException() throws InterruptedException {
        // given
        TimeUnit timeUnit = mock(TimeUnit.class);
        doThrow(new InterruptedException("TEST EXCEPTION")).when(timeUnit).sleep(10);

        // when
        new HttpResponse().withDelay(new Delay(timeUnit, 10)).applyDelay();
    }

    @Test
    public void shouldReturnFormattedRequestInToString() {
        assertEquals("{" + System.getProperty("line.separator") +
                        "  \"statusCode\" : 200," + System.getProperty("line.separator") +
                        "  \"body\" : \"c29tZV9ib2R5\"," + System.getProperty("line.separator") +
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"[A-Z]{0,10}\" ]" + System.getProperty("line.separator") +
                        "  } ]" + System.getProperty("line.separator") +
                        "}",
                response()
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        .toString()
        );
    }
}
