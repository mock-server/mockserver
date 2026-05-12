package org.mockserver.serialization.deserializers.request;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.*;
import org.mockserver.serialization.model.BinaryRequestDefinitionDTO;
import org.mockserver.serialization.model.BodyDTO;
import org.mockserver.serialization.model.DnsRequestDefinitionDTO;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.OpenAPIDefinitionDTO;
import org.mockserver.serialization.model.RequestDefinitionDTO;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;
import static org.mockserver.model.NottableString.string;
import static org.slf4j.event.Level.ERROR;

public class RequestDefinitionDTODeserializer extends StdDeserializer<RequestDefinitionDTO> {

    private static final long serialVersionUID = 1L;

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
            List<X509Certificate> clientCertificateChain = null;
            SocketAddress socketAddress = null;
            String localAddress = null;
            String remoteAddress = null;
            String specUrlOrPayload = null;
            String operationId = null;
            String contextPathPrefix = null;
            byte[] binaryData = null;
            String dnsName = null;
            DnsRecordType dnsType = null;
            DnsRecordClass dnsClass = null;
            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.currentName();
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
                        case "clientCertificateChain": {
                            jsonParser.nextToken();
                            X509Certificate[] certs = ctxt.readValue(jsonParser, X509Certificate[].class);
                            clientCertificateChain = certs != null ? Arrays.asList(certs) : null;
                            break;
                        }
                        case "localAddress": {
                            jsonParser.nextToken();
                            localAddress = ctxt.readValue(jsonParser, String.class);
                            break;
                        }
                        case "remoteAddress": {
                            jsonParser.nextToken();
                            remoteAddress = ctxt.readValue(jsonParser, String.class);
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
                        case "contextPathPrefix": {
                            jsonParser.nextToken();
                            contextPathPrefix = ctxt.readValue(jsonParser, String.class);
                            break;
                        }
                        case "binaryData": {
                            jsonParser.nextToken();
                            binaryData = ctxt.readValue(jsonParser, byte[].class);
                            break;
                        }
                        case "dnsName": {
                            jsonParser.nextToken();
                            dnsName = ctxt.readValue(jsonParser, String.class);
                            break;
                        }
                        case "dnsType": {
                            jsonParser.nextToken();
                            try {
                                dnsType = DnsRecordType.valueOf(ctxt.readValue(jsonParser, String.class));
                            } catch (IllegalArgumentException throwable) {
                                new MockServerLogger().logEvent(
                                    new LogEntry()
                                        .setType(EXCEPTION)
                                        .setLogLevel(ERROR)
                                        .setMessageFormat("exception while parsing dnsType value for RequestDefinitionDTO - {}")
                                        .setArguments(throwable.getMessage())
                                );
                            }
                            break;
                        }
                        case "dnsClass": {
                            jsonParser.nextToken();
                            try {
                                dnsClass = DnsRecordClass.valueOf(ctxt.readValue(jsonParser, String.class));
                            } catch (IllegalArgumentException throwable) {
                                new MockServerLogger().logEvent(
                                    new LogEntry()
                                        .setType(EXCEPTION)
                                        .setLogLevel(ERROR)
                                        .setMessageFormat("exception while parsing dnsClass value for RequestDefinitionDTO - {}")
                                        .setArguments(throwable.getMessage())
                                );
                            }
                            break;
                        }
                    }
                }
            }
            if (isNotBlank(dnsName)) {
                return (RequestDefinitionDTO) new DnsRequestDefinitionDTO()
                    .setDnsName(dnsName)
                    .setDnsType(dnsType)
                    .setDnsClass(dnsClass)
                    .setNot(not);
            } else if (binaryData != null) {
                return (RequestDefinitionDTO) new BinaryRequestDefinitionDTO()
                    .setBinaryData(binaryData)
                    .setSocketAddress(socketAddress)
                    .setNot(not);
            } else if (isNotBlank(specUrlOrPayload)) {
                return (RequestDefinitionDTO) new OpenAPIDefinitionDTO()
                    .setSpecUrlOrPayload(specUrlOrPayload)
                    .setOperationId(operationId)
                    .setContextPathPrefix(contextPathPrefix)
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
                    .setClientCertificateChain(clientCertificateChain)
                    .setSocketAddress(socketAddress)
                    .setLocalAddress(localAddress)
                    .setRemoteAddress(remoteAddress)
                    .setNot(not);
            }
        }
        return null;
    }
}
