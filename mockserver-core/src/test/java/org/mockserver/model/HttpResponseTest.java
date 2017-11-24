package org.mockserver.model;

import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertNull;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;
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
        assertEquals(Base64Converter.bytesToBase64String("somebody".getBytes()), new HttpResponse().withBody("somebody".getBytes()).getBodyAsString());
        assertEquals("somebody", new HttpResponse().withBody("somebody").getBodyAsString());
        assertNull(new HttpResponse().withBody((byte[]) null).getBodyAsString());
        assertEquals(null, new HttpResponse().withBody((String) null).getBodyAsString());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaders().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaders().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaders().get(0));
    }

    @Test
    public void returnsFirstHeaders() {
        assertEquals("value1", new HttpResponse().withHeaders(new Header("name", "value1")).getFirstHeader("name"));
        assertEquals("value1", new HttpResponse().withHeaders(new Header("name", "value1", "value2")).getFirstHeader("name"));
        assertEquals("value1", new HttpResponse().withHeaders(new Header("name", "value1", "value2"), new Header("name", "value3")).getFirstHeader("name"));
    }

    @Test
    public void returnsFirstHeaderIgnoringCase() {
        assertEquals("value1", new HttpResponse().withHeaders(new Header("NAME", "value1")).getFirstHeader("name"));
        assertEquals("value1", new HttpResponse().withHeaders(new Header("name", "value1", "value2")).getFirstHeader("NAME"));
        assertEquals("value1", new HttpResponse().withHeaders(new Header("NAME", "value1", "value2"), new Header("name", "value3")).getFirstHeader("name"));
    }

    @Test
    public void returnsHeaderByName() {
        assertThat(new HttpResponse().withHeaders(new Header("name", "value")).getHeader("name"), containsInAnyOrder("value"));
        assertThat(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).getHeader("name"), containsInAnyOrder("valueOne", "valueTwo"));
        assertThat(new HttpResponse().withHeader("name", "valueOne", "valueTwo").getHeader("name"), containsInAnyOrder("valueOne", "valueTwo"));
        assertThat(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).getHeader("otherName"), hasSize(0));
    }

    @Test
    public void containsHeaderIgnoringCase() {
        assertTrue(new HttpResponse().withHeaders(new Header("name", "value")).containsHeader("name", "value"));
        assertTrue(new HttpResponse().withHeaders(new Header("name", "value")).containsHeader("name", "VALUE"));
        assertTrue(new HttpResponse().withHeaders(new Header("name", "value")).containsHeader("NAME", "value"));
        assertTrue(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).containsHeader("name", "valueOne"));
        assertTrue(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).containsHeader("name", "VALUEONE"));
        assertTrue(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).containsHeader("NAME", "valueTwo"));
        assertTrue(new HttpResponse().withHeader("name", "valueOne", "valueTwo").containsHeader("name", "ValueOne"));
        assertTrue(new HttpResponse().withHeader("name", "valueOne", "valueTwo").containsHeader("name", "valueOne"));
        assertTrue(new HttpResponse().withHeader("name", "valueOne", "valueTwo").containsHeader("NAME", "ValueOne"));
        assertFalse(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).containsHeader("otherName", "valueOne"));
        assertFalse(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).containsHeader("name", "value"));
    }

    @Test
    public void returnsHeaderByNameIgnoringCase() {
        assertThat(new HttpResponse().withHeaders(new Header("Name", "value")).getHeader("name"), containsInAnyOrder("value"));
        assertThat(new HttpResponse().withHeaders(new Header("name", "valueOne", "valueTwo")).getHeader("Name"), containsInAnyOrder("valueOne", "valueTwo"));
        assertThat(new HttpResponse().withHeader("NAME", "valueOne", "valueTwo").getHeader("name"), containsInAnyOrder("valueOne", "valueTwo"));
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
    public void setsConnectionOptions() {
        assertEquals(
                new ConnectionOptions()
                        .withContentLengthHeaderOverride(10),
                new HttpResponse()
                        .withConnectionOptions(
                                new ConnectionOptions()
                                        .withContentLengthHeaderOverride(10)
                        )
                        .getConnectionOptions()
        );
    }

    @Test
    @Ignore("TimeUnit in Java 9 is final so it is not possible to mock it")
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
        assertEquals("{" + NEW_LINE +
                        "  \"headers\" : [ {" + NEW_LINE +
                        "    \"name\" : \"name\"," + NEW_LINE +
                        "    \"values\" : [ \"value\" ]" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"cookies\" : [ {" + NEW_LINE +
                        "    \"name\" : \"name\"," + NEW_LINE +
                        "    \"value\" : \"[A-Z]{0,10}\"" + NEW_LINE +
                        "  } ]," + NEW_LINE +
                        "  \"body\" : \"some_body\"" + NEW_LINE +
                        "}",
                response()
                        .withBody("some_body")
                        .withHeaders(new Header("name", "value"))
                        .withCookies(new Cookie("name", "[A-Z]{0,10}"))
                        .toString()
        );
    }
}
