package org.mockserver.serialization;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Joiner;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.LogEventRequestAndResponse;
import org.mockserver.serialization.model.LogEventRequestAndResponseDTO;
import org.slf4j.event.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class LogEventRequestAndResponseSerializer {
    private final MockServerLogger mockServerLogger;
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private JsonArraySerializer jsonArraySerializer = new JsonArraySerializer();
    private final ObjectWriter objectWriter;

    public LogEventRequestAndResponseSerializer(MockServerLogger mockServerLogger) {
        this.mockServerLogger = mockServerLogger;

        DefaultPrettyPrinter prettyPrinter = new DefaultPrettyPrinter()
            .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE)
            .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);

        objectWriter = ObjectMapperFactory.createObjectMapper().writer(prettyPrinter);
    }

    public String serialize(LogEventRequestAndResponse httpRequestAndHttpResponse) {
        try {
            return objectWriter.writeValueAsString(new LogEventRequestAndResponseDTO(httpRequestAndHttpResponse));
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing HttpRequestAndHttpResponse to JSON with value " + httpRequestAndHttpResponse)
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing HttpRequestAndHttpResponse to JSON with value " + httpRequestAndHttpResponse, e);
        }
    }

    public String serialize(List<LogEventRequestAndResponse> httpRequestAndHttpResponses) {
        return serialize(httpRequestAndHttpResponses.toArray(new LogEventRequestAndResponse[0]));
    }

    public String serialize(LogEventRequestAndResponse... httpRequestAndHttpResponses) {
        try {
            if (httpRequestAndHttpResponses != null && httpRequestAndHttpResponses.length > 0) {
                LogEventRequestAndResponseDTO[] httpRequestAndHttpResponseDTOS = new LogEventRequestAndResponseDTO[httpRequestAndHttpResponses.length];
                for (int i = 0; i < httpRequestAndHttpResponses.length; i++) {
                    httpRequestAndHttpResponseDTOS[i] = new LogEventRequestAndResponseDTO(httpRequestAndHttpResponses[i]);
                }
                return objectWriter
                    .withDefaultPrettyPrinter()
                    .writeValueAsString(httpRequestAndHttpResponseDTOS);
            } else {
                return "[]";
            }
        } catch (Exception e) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception while serializing HttpRequestAndHttpResponse to JSON with value " + Arrays.asList(httpRequestAndHttpResponses))
                    .setThrowable(e)
            );
            throw new RuntimeException("Exception while serializing HttpRequestAndHttpResponse to JSON with value " + Arrays.asList(httpRequestAndHttpResponses), e);
        }
    }

    public LogEventRequestAndResponse deserialize(String jsonHttpRequestAndHttpResponse) {
        if (isBlank(jsonHttpRequestAndHttpResponse)) {
            throw new IllegalArgumentException("1 error:" + NEW_LINE + " - a request is required but value was \"" + jsonHttpRequestAndHttpResponse + "\"");
        } else {
            LogEventRequestAndResponse httpRequestAndHttpResponse = null;
            try {
                LogEventRequestAndResponseDTO httpRequestAndHttpResponseDTO = objectMapper.readValue(jsonHttpRequestAndHttpResponse, LogEventRequestAndResponseDTO.class);
                if (httpRequestAndHttpResponseDTO != null) {
                    httpRequestAndHttpResponse = httpRequestAndHttpResponseDTO.buildObject();
                }
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception while parsing{}for HttpRequestAndHttpResponse " + throwable.getMessage())
                        .setArguments(jsonHttpRequestAndHttpResponse)
                        .setThrowable(throwable)
                );
                throw new RuntimeException("Exception while parsing [" + jsonHttpRequestAndHttpResponse + "] for HttpRequestAndHttpResponse", throwable);
            }
            return httpRequestAndHttpResponse;
        }
    }

    public LogEventRequestAndResponse[] deserializeArray(String jsonHttpRequestAndHttpResponse) {
        List<LogEventRequestAndResponse> httpRequestAndHttpResponses = new ArrayList<>();
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
        return httpRequestAndHttpResponses.toArray(new LogEventRequestAndResponse[0]);
    }

}
