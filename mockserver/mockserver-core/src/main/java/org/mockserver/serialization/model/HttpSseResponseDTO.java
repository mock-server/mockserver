package org.mockserver.serialization.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.mockserver.model.Header;
import org.mockserver.model.Headers;
import org.mockserver.model.HttpSseResponse;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.ArrayList;
import java.util.List;

public class HttpSseResponseDTO extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<HttpSseResponse> {
    private DelayDTO delay;
    private Integer statusCode;
    private Headers headers;
    private List<SseEventDTO> events;
    private Boolean closeConnection;
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean primary;

    public HttpSseResponseDTO(HttpSseResponse httpSseResponse) {
        if (httpSseResponse != null) {
            if (httpSseResponse.getDelay() != null) {
                delay = new DelayDTO(httpSseResponse.getDelay());
            }
            statusCode = httpSseResponse.getStatusCode();
            headers = httpSseResponse.getHeaders();
            closeConnection = httpSseResponse.getCloseConnection();
            if (httpSseResponse.getEvents() != null) {
                events = new ArrayList<>();
                httpSseResponse.getEvents().forEach(event -> events.add(new SseEventDTO(event)));
            }
            primary = httpSseResponse.isPrimary();
        }
    }

    public HttpSseResponseDTO() {
    }

    public HttpSseResponse buildObject() {
        HttpSseResponse httpSseResponse = new HttpSseResponse()
            .withDelay(delay != null ? delay.buildObject() : null)
            .withStatusCode(statusCode)
            .withHeaders(headers)
            .withCloseConnection(closeConnection)
            .withPrimary(primary);
        if (events != null) {
            events.forEach(eventDTO -> httpSseResponse.withEvent(eventDTO.buildObject()));
        }
        return httpSseResponse;
    }

    public DelayDTO getDelay() {
        return delay;
    }

    public HttpSseResponseDTO setDelay(DelayDTO delay) {
        this.delay = delay;
        return this;
    }

    public Integer getStatusCode() {
        return statusCode;
    }

    public HttpSseResponseDTO setStatusCode(Integer statusCode) {
        this.statusCode = statusCode;
        return this;
    }

    public Headers getHeaders() {
        return headers;
    }

    public HttpSseResponseDTO setHeaders(Headers headers) {
        this.headers = headers;
        return this;
    }

    public List<SseEventDTO> getEvents() {
        return events;
    }

    public HttpSseResponseDTO setEvents(List<SseEventDTO> events) {
        this.events = events;
        return this;
    }

    public Boolean getCloseConnection() {
        return closeConnection;
    }

    public HttpSseResponseDTO setCloseConnection(Boolean closeConnection) {
        this.closeConnection = closeConnection;
        return this;
    }

    public boolean isPrimary() {
        return primary;
    }

    public HttpSseResponseDTO setPrimary(boolean primary) {
        this.primary = primary;
        return this;
    }
}
