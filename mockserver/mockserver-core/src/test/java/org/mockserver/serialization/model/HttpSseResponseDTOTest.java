package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class HttpSseResponseDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        Delay delay = new Delay(TimeUnit.HOURS, 1);
        Integer statusCode = 200;
        Boolean closeConnection = Boolean.TRUE;
        Headers headers = new Headers(new Header("Content-Type", "text/event-stream"));
        SseEvent event1 = SseEvent.sseEvent().withEvent("message").withData("hello").withId("1").withRetry(3000);
        SseEvent event2 = SseEvent.sseEvent().withEvent("update").withData("world").withId("2");

        HttpSseResponse httpSseResponse = HttpSseResponse.sseResponse()
                .withDelay(delay)
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withCloseConnection(closeConnection)
                .withEvents(event1, event2);

        // when
        HttpSseResponseDTO httpSseResponseDTO = new HttpSseResponseDTO(httpSseResponse);

        // then
        assertThat(httpSseResponseDTO.getDelay(), is(new DelayDTO(delay)));
        assertThat(httpSseResponseDTO.getStatusCode(), is(statusCode));
        assertThat(httpSseResponseDTO.getHeaders(), is(headers));
        assertThat(httpSseResponseDTO.getCloseConnection(), is(closeConnection));
        assertThat(httpSseResponseDTO.getEvents(), is(Arrays.asList(new SseEventDTO(event1), new SseEventDTO(event2))));
    }

    @Test
    public void shouldBuildObject() {
        // given
        Delay delay = new Delay(TimeUnit.HOURS, 1);
        Integer statusCode = 200;
        Boolean closeConnection = Boolean.TRUE;
        Headers headers = new Headers(new Header("Content-Type", "text/event-stream"));
        SseEvent event1 = SseEvent.sseEvent().withEvent("message").withData("hello").withId("1").withRetry(3000);
        SseEvent event2 = SseEvent.sseEvent().withEvent("update").withData("world").withId("2");

        HttpSseResponse httpSseResponse = HttpSseResponse.sseResponse()
                .withDelay(delay)
                .withStatusCode(statusCode)
                .withHeaders(headers)
                .withCloseConnection(closeConnection)
                .withEvents(event1, event2);

        // when
        HttpSseResponse builtHttpSseResponse = new HttpSseResponseDTO(httpSseResponse).buildObject();

        // then
        assertThat(builtHttpSseResponse.getDelay(), is(delay));
        assertThat(builtHttpSseResponse.getStatusCode(), is(statusCode));
        assertThat(builtHttpSseResponse.getHeaders(), is(headers));
        assertThat(builtHttpSseResponse.getCloseConnection(), is(closeConnection));
        assertThat(builtHttpSseResponse.getEvents(), is(Arrays.asList(event1, event2)));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        DelayDTO delay = new DelayDTO(new Delay(TimeUnit.HOURS, 1));
        Integer statusCode = 200;
        Boolean closeConnection = Boolean.TRUE;
        Headers headers = new Headers(new Header("Content-Type", "text/event-stream"));
        List<SseEventDTO> events = Arrays.asList(
                new SseEventDTO(SseEvent.sseEvent().withEvent("message").withData("hello")),
                new SseEventDTO(SseEvent.sseEvent().withEvent("update").withData("world"))
        );

        HttpSseResponse httpSseResponse = HttpSseResponse.sseResponse();

        // when
        HttpSseResponseDTO httpSseResponseDTO = new HttpSseResponseDTO(httpSseResponse);
        httpSseResponseDTO.setDelay(delay);
        httpSseResponseDTO.setStatusCode(statusCode);
        httpSseResponseDTO.setHeaders(headers);
        httpSseResponseDTO.setCloseConnection(closeConnection);
        httpSseResponseDTO.setEvents(events);

        // then
        assertThat(httpSseResponseDTO.getDelay(), is(delay));
        assertThat(httpSseResponseDTO.getStatusCode(), is(statusCode));
        assertThat(httpSseResponseDTO.getHeaders(), is(headers));
        assertThat(httpSseResponseDTO.getCloseConnection(), is(closeConnection));
        assertThat(httpSseResponseDTO.getEvents(), is(events));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        HttpSseResponseDTO httpSseResponseDTO = new HttpSseResponseDTO(null);

        // then
        assertThat(httpSseResponseDTO.getDelay(), is(nullValue()));
        assertThat(httpSseResponseDTO.getStatusCode(), is(nullValue()));
        assertThat(httpSseResponseDTO.getHeaders(), is(nullValue()));
        assertThat(httpSseResponseDTO.getCloseConnection(), is(nullValue()));
        assertThat(httpSseResponseDTO.getEvents(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        HttpSseResponseDTO httpSseResponseDTO = new HttpSseResponseDTO(HttpSseResponse.sseResponse());

        // then
        assertThat(httpSseResponseDTO.getDelay(), is(nullValue()));
        assertThat(httpSseResponseDTO.getStatusCode(), is(nullValue()));
        assertThat(httpSseResponseDTO.getHeaders(), is(nullValue()));
        assertThat(httpSseResponseDTO.getCloseConnection(), is(nullValue()));
        assertThat(httpSseResponseDTO.getEvents(), is(nullValue()));
    }
}
