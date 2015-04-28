package org.mockserver.model;

import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
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
        assertEquals(Base64Converter.stringToBase64Bytes("somebody".getBytes()), new HttpResponse().withBody("somebody".getBytes()).getBodyAsString());
        assertEquals("somebody", new HttpResponse().withBody("somebody").getBodyAsString());
        assertNull(new HttpResponse().withBody((byte[]) null).getBodyAsString());
        assertEquals("", new HttpResponse().withBody((String) null).getBodyAsString());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaders().get(0));
    }

    @Test
    public void returnsHeaderByName() {
        assertThat(new HttpResponse().withHeaders(new Header("name", "value")).getHeader("name"), containsInAnyOrder("value"));
        assertThat(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).getHeader("name"), containsInAnyOrder("valueOne", "valueTwo"));
        assertThat(new HttpResponse().withHeader("name", "valueOne", "valueTwo").getHeader("name"), containsInAnyOrder("valueOne", "valueTwo"));
        assertThat(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).getHeader("otherName"), hasSize(0));
    }

    @Test
    public void addDuplicateHeader() {
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).withHeader(new Header("name", "valueTwo")).getHeaders(), containsInAnyOrder(new Header("name", "valueOne", "valueTwo")));
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).withHeader("name", "valueTwo").getHeaders(), containsInAnyOrder(new Header("name", "valueOne", "valueTwo")));
    }

    @Test
    public void updatesExistingHeader() {
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).updateHeader(new Header("name", "valueTwo")).getHeaders(), containsInAnyOrder(new Header("name", "valueTwo")));
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).updateHeader("name", "valueTwo").getHeaders(), containsInAnyOrder(new Header("name", "valueTwo")));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", ""), new HttpResponse().withCookies(new Cookie("name", "")).getCookies().get(0));
        assertEquals(new Cookie("name", null), new HttpResponse().withCookies(new Cookie("name", null)).getCookies().get(0));

        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(Arrays.asList(new Cookie("name", "value"))).getCookies().get(0));

        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookie(new Cookie("name", "value")).getCookies().get(0));
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookie("name", "value").getCookies().get(0));
        assertEquals(new Cookie("name", ""), new HttpResponse().withCookie(new Cookie("name", "")).getCookies().get(0));
        assertEquals(new Cookie("name", null), new HttpResponse().withCookie(new Cookie("name", null)).getCookies().get(0));
    }

    @Test
    public void setsDelay() {
        assertEquals(new Delay(TimeUnit.MILLISECONDS, 10), new HttpResponse().withDelay(new Delay(TimeUnit.MILLISECONDS, 10)).getDelay());
        assertEquals(new Delay(TimeUnit.MILLISECONDS, 10), new HttpResponse().withDelay(TimeUnit.MILLISECONDS, 10).getDelay());
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
                        "  \"headers\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"values\" : [ \"value\" ]" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"cookies\" : [ {" + System.getProperty("line.separator") +
                        "    \"name\" : \"name\"," + System.getProperty("line.separator") +
                        "    \"value\" : \"[A-Z]{0,10}\"" + System.getProperty("line.separator") +
                        "  } ]," + System.getProperty("line.separator") +
                        "  \"body\" : \"some_body\"" + System.getProperty("line.separator") +
                        "}",
                response()
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        .toString()
        );
    }
}
