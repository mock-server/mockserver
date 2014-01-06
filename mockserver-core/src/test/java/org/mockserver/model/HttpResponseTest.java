package org.mockserver.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.mockito.Mockito.*;

/**
 * @author jamesdbloom
 */
public class HttpResponseTest {

    @Test
    public void shouldAlwaysCreateNewObject() {
        assertEquals(new HttpResponse().response(), HttpResponse.response());
        assertNotSame(HttpResponse.response(), HttpResponse.response());
    }

    @Test
    public void returnsResponseCode() {
        assertEquals(new Integer(200), new HttpResponse().withStatusCode(200).getStatusCode());
    }

    @Test
    public void returnsBody() {
        assertEquals("somebody", new HttpResponse().withBody("somebody").getBodyAsString());
    }

    @Test
    public void returnsHeaders() {
        assertEquals(new Header("name", "value"), new HttpResponse().withHeaders(new Header("name", "value")).getHeaders().get(0));
    }

    @Test
    public void returnsCookies() {
        assertEquals(new Cookie("name", "value"), new HttpResponse().withCookies(new Cookie("name", "value")).getCookies().get(0));
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
        doThrow(new InterruptedException()).when(timeUnit).sleep(10);

        // when
        new HttpResponse().withDelay(new Delay(timeUnit, 10)).applyDelay();
    }
}
