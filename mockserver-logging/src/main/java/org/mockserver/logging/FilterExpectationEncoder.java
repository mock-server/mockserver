package org.mockserver.logging;

import java.io.IOException;

import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.ExpectationDTO;
import org.mockserver.client.serialization.model.HttpRequestDTO;
import org.mockserver.client.serialization.model.HttpResponseDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.encoder.EncoderBase;

/**
 * Remove dependant on configuration request and response headers and cookies from json log
 */
public class FilterExpectationEncoder<E> extends EncoderBase<E> {
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    private boolean excludeRequestHeaders = true;
    private boolean excludeResponseHeaders = true;
    private boolean excludeRequestCookies = true;
    private boolean excludeResponseCookies = true;
    private boolean prettyPrint = true;

    @Override
    public void doEncode(E event) throws IOException {
        ILoggingEvent loggingEvent = (ILoggingEvent) event;
        ExpectationDTO expectationDTO = objectMapper.readValue(loggingEvent.getMessage(), ExpectationDTO.class);

        HttpRequestDTO httpRequest = expectationDTO.getHttpRequest();
        if (excludeRequestHeaders) {
            httpRequest.getHeaders().clear();
        }
        if (excludeRequestCookies) {
            httpRequest.getCookies().clear();
        }

        HttpResponseDTO httpResponse = expectationDTO.getHttpResponse();
        if (excludeResponseHeaders) {
            httpResponse.getHeaders().clear();
        }
        if (excludeResponseCookies) {
            httpResponse.getCookies().clear();
        }

        ObjectWriter objectWriter;
        if (prettyPrint) {
            objectWriter = objectMapper.writerWithDefaultPrettyPrinter();
        } else {
            objectWriter = objectMapper.writer();
        }

        objectWriter.writeValue(outputStream, expectationDTO);
        outputStream.flush();
    }

    @Override
    public void close() throws IOException {
        // nothing to do
    }

    // The following methods are for binding encoder config of logback.xml

    public void addExcludeRequestHeaders(String excludeRequestHeaders) {
        this.excludeRequestHeaders = Boolean.valueOf(excludeRequestHeaders);
    }

    public void addExcludeResponseHeaders(String excludeResponseHeaders) {
        this.excludeResponseHeaders = Boolean.valueOf(excludeResponseHeaders);
    }

    public void addExcludeRequestCookies(String excludeRequestCookies) {
        this.excludeRequestCookies = Boolean.valueOf(excludeRequestCookies);
    }

    public void addExcludeResponseCookies(String excludeResponseCookies) {
        this.excludeResponseCookies = Boolean.valueOf(excludeResponseCookies);
    }

    public void addPrettyPrint(String prettyPrint) {
        this.prettyPrint = Boolean.valueOf(prettyPrint);
    }
}
