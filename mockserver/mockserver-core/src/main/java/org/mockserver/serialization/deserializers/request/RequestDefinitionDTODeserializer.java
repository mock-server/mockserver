package org.mockserver.serialization.deserializers.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.model.BodyDTO;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.OpenAPIDefinitionDTO;
import org.mockserver.serialization.model.RequestDefinitionDTO;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.ERROR;

public class RequestDefinitionDTODeserializer extends StdDeserializer<RequestDefinitionDTO> {

    public RequestDefinitionDTODeserializer() {
        super(RequestDefinitionDTO.class);
    }

    @Override
    public RequestDefinitionDTO deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            Boolean not = null;
            NottableString method = string("");
            NottableString path = string("");
            Parameters pathParameters = null;
            Parameters queryStringParameters = null;
            BodyDTO body = null;
            Cookies cookies = null;
            Headers headers = null;
            Boolean keepAlive = null;
            Boolean secure = null;
            Protocol protocol = null;
            SocketAddress socketAddress = null;
            String specUrlOrPayload = null;
            String operationId = null;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.getCurrentName();
                if (fieldName != null) {
                    switch (fieldName) {
                        case "not": {
                            jsonParser.nextToken();
                            not = jsonParser.getBooleanValue();
                            break;
                        }
                        case "method": {
                            jsonParser.nextToken();
                            method = ctxt.readValue(jsonParser, NottableString.class);
                            break;
                        }
                        case "path": {
                            jsonParser.nextToken();
                            path = ctxt.readValue(jsonParser, NottableString.class);
                            break;
                        }
                        case "pathParameters": {
                            jsonParser.nextToken();
                            pathParameters = ctxt.readValue(jsonParser, Parameters.class);
                            break;
                        }
                        case "queryStringParameters": {
                            jsonParser.nextToken();
                            queryStringParameters = ctxt.readValue(jsonParser, Parameters.class);
                            break;
                        }
                        case "body": {
                            jsonParser.nextToken();
                            body = ctxt.readValue(jsonParser, BodyDTO.class);
                            break;
                        }
                        case "cookies": {
                            jsonParser.nextToken();
                            cookies = ctxt.readValue(jsonParser, Cookies.class);
                            break;
                        }
                        case "headers": {
                            jsonParser.nextToken();
                            headers = ctxt.readValue(jsonParser, Headers.class);
                            break;
                        }
                        case "keepAlive": {
                            jsonParser.nextToken();
                            keepAlive = ctxt.readValue(jsonParser, Boolean.class);
                            break;
                        }
                        case "secure": {
                            jsonParser.nextToken();
                            secure = ctxt.readValue(jsonParser, Boolean.class);
                            break;
                        }
                        case "socketAddress": {
                            jsonParser.nextToken();
                            socketAddress = ctxt.readValue(jsonParser, SocketAddress.class);
                            break;
                        }
                        case "protocol": {
                            jsonParser.nextToken();
                            try {
                                protocol = Protocol.valueOf(ctxt.readValue(jsonParser, String.class));
                            } catch (Throwable throwable) {
                                new MockServerLogger().logEvent(
                                    new LogEntry()
                                        .setType(EXCEPTION)
                                        .setLogLevel(ERROR)
                                        .setMessageFormat("exception while parsing protocol value for RequestDefinitionDTO - " + throwable.getMessage())
                                        .setThrowable(throwable)
                                );
                            }
                            break;
                        }
                        case "specUrlOrPayload": {
                            jsonParser.nextToken();
                            JsonNode potentiallyJsonField = ctxt.readValue(jsonParser, JsonNode.class);
                            if (potentiallyJsonField.isTextual()) {
                                specUrlOrPayload = potentiallyJsonField.asText();
                            } else {
                                specUrlOrPayload = potentiallyJsonField.toPrettyString();
                            }
                            break;
                        }
                        case "operationId": {
                            jsonParser.nextToken();
                            operationId = ctxt.readValue(jsonParser, String.class);
                            break;
                        }
                    }
                }
            }
            if (isNotBlank(specUrlOrPayload)) {
                return (RequestDefinitionDTO) new OpenAPIDefinitionDTO()
                    .setSpecUrlOrPayload(specUrlOrPayload)
                    .setOperationId(operationId)
                    .setNot(not);
            } else {
                return (RequestDefinitionDTO) new HttpRequestDTO()
                    .setMethod(method)
                    .setPath(path)
                    .setPathParameters(pathParameters)
                    .setQueryStringParameters(queryStringParameters)
                    .setBody(body)
                    .setCookies(cookies)
                    .setHeaders(headers)
                    .setKeepAlive(keepAlive)
                    .setSecure(secure)
                    .setProtocol(protocol)
                    .setSocketAddress(socketAddress)
                    .setNot(not);
            }
        }
        return null;
    }
}
