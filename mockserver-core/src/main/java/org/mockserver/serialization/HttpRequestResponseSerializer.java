package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequestAndHttpResponse;
import org.mockserver.serialization.model.HttpRequestAndHttpResponseDTO;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpRequestResponseSerializer {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private final ObjectWriter objectWriter;

    public HttpRequestResponseSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter()
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        objectWriter = ObjectMapperFactory.createObjectMapper().writer(prettyPrinter);
    }

    public String serialize(HttpRequestAndHttpResponse httpRequestAndHttpResponse) {
        try {
            return objectWriter.writeValueAsString(new HttpRequestAndHttpResponseDTO(httpRequestAndHttpResponse));
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while serializing HttpRequestAndHttpResponse to JSON with value " + httpRequestAndHttpResponse)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing HttpRequestAndHttpResponse to JSON with value " + httpRequestAndHttpResponse, e);
        }
    }

    public String serialize(List<HttpRequestAndHttpResponse> httpRequestAndHttpResponses) {
        return serialize(httpRequestAndHttpResponses.toArray(new HttpRequestAndHttpResponse[0]));
    }

    public String serialize(HttpRequestAndHttpResponse... httpRequestAndHttpResponses) {
        try {
            if (httpRequestAndHttpResponses != null && httpRequestAndHttpResponses.length > 0) {
                HttpRequestAndHttpResponseDTO[] httpRequestAndHttpResponseDTOS = new HttpRequestAndHttpResponseDTO[httpRequestAndHttpResponses.length];
                for (int i = 0; i < httpRequestAndHttpResponses.length; i++) {
                    httpRequestAndHttpResponseDTOS[i] = new HttpRequestAndHttpResponseDTO(httpRequestAndHttpResponses[i]);
                }
                return objectWriter.writeValueAsString(httpRequestAndHttpResponseDTOS);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setType(LogEntry.LogMessageType.EXCEPTION)
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("Exception while serializing HttpRequestAndHttpResponse to JSON with value " + Arrays.asList(httpRequestAndHttpResponses))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing HttpRequestAndHttpResponse to JSON with value " + Arrays.asList(httpRequestAndHttpResponses), e);
        }
    }

    public HttpRequestAndHttpResponse deserialize(String jsonHttpRequestAndHttpResponse) {
        if (isBlank(jsonHttpRequestAndHttpResponse)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request is required but value was \"" + jsonHttpRequestAndHttpResponse + "\"");
        } else {
            HttpRequestAndHttpResponse httpRequestAndHttpResponse = null;
            try {
                HttpRequestAndHttpResponseDTO httpRequestAndHttpResponseDTO = objectMapper.readValue(jsonHttpRequestAndHttpResponse, HttpRequestAndHttpResponseDTO.class);
                if (httpRequestAndHttpResponseDTO != null) {
                    httpRequestAndHttpResponse = httpRequestAndHttpResponseDTO.buildObject();
                }
            } catch (Exception e) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(LogEntry.LogMessageType.EXCEPTION)
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing {} for HttpRequestAndHttpResponse")
                        .setArguments(jsonHttpRequestAndHttpResponse)
                        .setThrowable(e)
                );
                throw new RuntimeException("Exception while parsing [" + jsonHttpRequestAndHttpResponse + "] for HttpRequestAndHttpResponse", e);
            }
            return httpRequestAndHttpResponse;
        }
    }

    public HttpRequestAndHttpResponse[] deserializeArray(String jsonHttpRequestAndHttpResponse) {
        List<HttpRequestAndHttpResponse> httpRequestAndHttpResponses = new ArrayList<>();
        if (isBlank(jsonHttpRequestAndHttpResponse)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or request array is required but value was \"" + jsonHttpRequestAndHttpResponse + "\"");
        } else {
            List<String> jsonRequestList = jsonArraySerializer.returnJSONObjects(jsonHttpRequestAndHttpResponse);
            if (jsonRequestList.isEmpty()) {
                throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request or array of request is required");
            } else {
                List<String> validationErrorsList = new ArrayList<String>();
                for (String jsonRequest : jsonRequestList) {
                    try {
                        httpRequestAndHttpResponses.add(deserialize(jsonRequest));
                    } catch (IllegalArgumentException iae) {
                        validationErrorsList.add(iae.getMessage());
                    }

                }
                if (!validationErrorsList.isEmpty()) {
                    throw new IllegalArgumentException((validationErrorsList.size() > 1 ? "[" : "") + Joiner.on("," + NEW_LINE).join(validationErrorsList) + (validationErrorsList.size() > 1 ? "]" : ""));
                }
            }
        }
        return httpRequestAndHttpResponses.toArray(new HttpRequestAndHttpResponse[0]);
    }

}
