package org.mockserver.model;

import org.junit.Test;
import org.mockserver.client.serialization.Base64Converter;

import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Charsets.UTF_8;
import static java.util.concurrent.TimeUnit.SECONDS;
import static junit.framework.TestCase.*;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.number.OrderingComparison.greaterThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.*;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.ConnectionOptions.connectionOptions;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class HttpResponseTest {

    private final Base64Converter base64Converter = new Base64Converter();

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpResponse().response(), response());
        assertNotSame(response(), response());
    }

    @Test
    public void returnsResponseStatusCode() {
        assertEquals(new Integer(200), new HttpResponse().withStatusCode(200).getStatusCode());
    }

    @Test
    public void returnsResponseReasonPhrase() {
        assertEquals("reasonPhrase", new HttpResponse().withReasonPhrase("reasonPhrase").getReasonPhrase());
    }

    @Test
    public void returnsBody() {
        assertEquals(base64Converter.bytesToBase64String("somebody".getBytes(UTF_8)), new HttpResponse().withBody("somebody".getBytes(UTF_8)).getBodyAsString());
        assertEquals("somebody", new HttpResponse().withBody("somebody").getBodyAsString());
        assertNull(new HttpResponse().withBody((byte[]) null).getBodyAsString());
        assertEquals(null, new HttpResponse().withBody((String) null).getBodyAsString());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(new Header("name", "value")).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(Arrays.asList(new Header("name", "value"))).getHeaderList().get(0));
        assertEquals(new Header("name", "value"), new HttpRequest().withHeader(new Header("name", "value")).getHeaderList().get(0));
        assertEquals(new Header("name", "value_one", "value_two"), new HttpRequest().withHeader(new Header("name", "value_one")).withHeader(new Header("name", "value_two")).getHeaderList().get(0));
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
        assertEquals("value1", new HttpResponse().withHeaders(new Header("NAME", "value1", "value2"), new Header("NAME", "value3"), new Header("NAME", "value4")).getFirstHeader("NAME"));
        assertEquals("value1", new HttpResponse().withHeaders(new Header("name", "value1", "value2"), new Header("name", "value3"), new Header("name", "value4")).getFirstHeader("NAME"));
        assertEquals("value1", new HttpResponse().withHeaders(new Header("NAME", "value1", "value2"), new Header("name", "value3"), new Header("name", "value4")).getFirstHeader("name"));
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
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).withHeader(new Header("name", "valueTwo")).getHeaderList(), containsInAnyOrder(new Header("name", "valueOne", "valueTwo")));
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).withHeader("name", "valueTwo").getHeaderList(), containsInAnyOrder(new Header("name", "valueOne", "valueTwo")));
    }

    @Test
    public void updatesExistingHeader() {
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).replaceHeader(new Header("name", "valueTwo")).getHeaderList(), containsInAnyOrder(new Header("name", "valueTwo")));
        assertThat(new HttpResponse().withHeader(new Header("name", "valueOne")).replaceHeader("name", "valueTwo").getHeaderList(), containsInAnyOrder(new Header("name", "valueTwo")));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(new Cookie("name", "value")).getCookieList().get(0));
        assertEquals(new Cookie("name", ""), new HttpResponse().withCookies(new Cookie("name", "")).getCookieList().get(0));
        assertEquals(new Cookie("name", null), new HttpResponse().withCookies(new Cookie("name", null)).getCookieList().get(0));

        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(Arrays.asList(new Cookie("name", "value"))).getCookieList().get(0));

        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookie(new Cookie("name", "value")).getCookieList().get(0));
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookie("name", "value").getCookieList().get(0));
        assertEquals(new Cookie("name", ""), new HttpResponse().withCookie(new Cookie("name", "")).getCookieList().get(0));
        assertEquals(new Cookie("name", null), new HttpResponse().withCookie(new Cookie("name", null)).getCookieList().get(0));
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
    public void appliesDelay() throws InterruptedException {
        // when
        long before = System.currentTimeMillis();
        new HttpResponse().withDelay(new Delay(SECONDS, 3)).applyDelay();

        // then
        assertThat(System.currentTimeMillis() - before, greaterThan(SECONDS.toMillis(2)));
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
                "  \"statusCode\" : 666," + NEW_LINE +
                "  \"reasonPhrase\" : \"randomPhrase\"," + NEW_LINE +
                "  \"headers\" : {" + NEW_LINE +
                "    \"some_header\" : [ \"some_header_value\" ]" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"cookies\" : {" + NEW_LINE +
                "    \"some_cookie\" : \"some_cookie_value\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"body\" : {" + NEW_LINE +
                "    \"type\" : \"STRING\"," + NEW_LINE +
                "    \"string\" : \"some_body\"," + NEW_LINE +
                "    \"contentType\" : \"text/plain; charset=utf-8\"" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"delay\" : {" + NEW_LINE +
                "    \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "    \"value\" : 15" + NEW_LINE +
                "  }," + NEW_LINE +
                "  \"connectionOptions\" : {" + NEW_LINE +
                "    \"contentLengthHeaderOverride\" : 10," + NEW_LINE +
                "    \"keepAliveOverride\" : true" + NEW_LINE +
                "  }" + NEW_LINE +
                "}",
            response()
                .withBody("some_body", UTF_8)
                .withStatusCode(666)
                .withReasonPhrase("randomPhrase")
                .withHeaders(new Header("some_header", "some_header_value"))
                .withCookies(new Cookie("some_cookie", "some_cookie_value"))
                .withConnectionOptions(
                    connectionOptions()
                        .withContentLengthHeaderOverride(10)
                        .withKeepAliveOverride(true)
                )
                .withDelay(SECONDS, 15)
                .toString()
        );
    }

    @Test
    public void shouldClone() {
        // given
        HttpResponse responseOne = response()
            .withBody("some_body", UTF_8)
            .withStatusCode(666)
            .withReasonPhrase("someReasonPhrase")
            .withHeader("some_header", "some_header_value")
            .withCookie("some_cookie", "some_cookie_value")
            .withConnectionOptions(
                connectionOptions()
                    .withContentLengthHeaderOverride(10)
                    .withKeepAliveOverride(true)
            )
            .withDelay(SECONDS, 15);

        // when
        HttpResponse responseTwo = responseOne.clone();

        // then
        assertThat(responseOne, not(same(responseTwo)));
        assertThat(responseOne, is(responseTwo));
    }
}
