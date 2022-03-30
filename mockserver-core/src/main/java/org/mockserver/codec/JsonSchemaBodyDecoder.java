package org.mockserver.codec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.*;
import com.google.common.base.Joiner;
import org.mockserver.configuration.Configuration;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.BodyMatcher;
import org.mockserver.matchers.JsonSchemaMatcher;
import org.mockserver.xml.StringToXmlDocumentParser;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;
import java.util.*;

import static java.lang.Double.parseDouble;
import static java.lang.Long.parseLong;
import static java.util.jar.Attributes.Name.CONTENT_TYPE;
import static java.util.stream.Collectors.toList;
import static org.mockserver.formatting.StringFormatter.formatLogMessage;
import static org.mockserver.log.model.LogEntry.LogMessageType.EXCEPTION;
import static org.mockserver.xml.StringToXmlDocumentParser.ErrorLevel.FATAL_ERROR;
import static org.mockserver.xml.StringToXmlDocumentParser.ErrorLevel.prettyPrint;
import static org.mockserver.model.NottableString.serialiseNottableString;

public class JsonSchemaBodyDecoder {

    private static final String APPLICATION_XML = "application/xml";
    private static final String TEXT_XML = "text/xml";
    private static final String APPLICATION_X_WWW_FORM_URLENCODED = "application/x-www-form-urlencoded";
    private final Configuration configuration;
    private final MockServerLogger mockServerLogger;
    private final Expectation expectation;
    private final HttpRequest httpRequest;
    private final ExpandedParameterDecoder formParameterParser;

    public JsonSchemaBodyDecoder(Configuration configuration, MockServerLogger mockServerLogger, Expectation expectation, HttpRequest httpRequest) {
        this.configuration = configuration;
        this.mockServerLogger = mockServerLogger;
        this.expectation = expectation;
        this.httpRequest = httpRequest;
        formParameterParser = new ExpandedParameterDecoder(configuration, mockServerLogger);
    }

    public String convertToJson(HttpRequest request, BodyMatcher<?> bodyMatcher) {
        String bodyAsJson = request.getBodyAsString();
        String contentType = request.getFirstHeader(CONTENT_TYPE.toString());
        if (contentType.contains(APPLICATION_XML) || contentType.contains(TEXT_XML)) {
            try {
                Map<StringToXmlDocumentParser.ErrorLevel, String> errors = new HashMap<>();
                Document document = new StringToXmlDocumentParser().buildDocument(request.getBodyAsString(), (matchedInException, throwable, level) -> {
                    errors.put(level, throwable.getMessage());
                });
                if (errors.containsKey(FATAL_ERROR)) {
                    throw new IllegalArgumentException(formatLogMessage("failed to convert:{}to json for json schema matcher:{}", request.getBodyAsString(), bodyMatcher, Joiner.on("\n").join(errors.values())));
                }
                for (Map.Entry<StringToXmlDocumentParser.ErrorLevel, String> errorEntry : errors.entrySet()) {
                    mockServerLogger.logEvent(
                        new LogEntry()
                            .setLogLevel(Level.ERROR)
                            .setMessageFormat("failed to convert:{}to json for json schema matcher:{}")
                            .setArguments(request.getBodyAsString(), bodyMatcher, prettyPrint(errorEntry.getKey()) + ": " + errorEntry.getValue())
                    );
                }
                Object objectMap = xmlToMap(document.getFirstChild());
                bodyAsJson = ObjectMapperFactory.createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(objectMap);
            } catch (Throwable throwable) {
                mockServerLogger.logEvent(
                    new LogEntry()
                        .setType(EXCEPTION)
                        .setHttpRequest(request)
                        .setExpectation(this.expectation)
                        .setMessageFormat("exception parsing xml body for{}while matching against request{}")
                        .setArguments(request, this.httpRequest)
                );
            }
        } else if (contentType.contains(APPLICATION_X_WWW_FORM_URLENCODED)) {
            ObjectNode objectNode = new ObjectNode(JsonNodeFactory.instance);
            Parameters parameters = formParameterParser
                .retrieveFormParameters(request.getBodyAsString(), false);
            if (bodyMatcher instanceof JsonSchemaMatcher) {
                splitParameters(((JsonSchemaMatcher) bodyMatcher).getParameterStyle(), parameters);
            }
            parameters
                .getEntries()
                .forEach(parameter -> objectNode.set(serialiseNottableString(parameter.getName()), toJsonObject(NottableString.serialiseNottableStrings(parameter.getValues()))));
            bodyAsJson = objectNode.toPrettyString();
        }
        return bodyAsJson;
    }


    private void splitParameters(Map<String, ParameterStyle> parameterStyles, Parameters bodyParameters) {
        if (parameterStyles != null && bodyParameters != null) {
            for (Map.Entry<String, ParameterStyle> parameterStyleEntry : parameterStyles.entrySet()) {
                for (Parameter bodyParameterEntry : bodyParameters.getEntries()) {
                    if (parameterStyleEntry.getKey().equals(bodyParameterEntry.getName().getValue())) {
                        bodyParameterEntry.replaceValues(new ExpandedParameterDecoder(configuration, mockServerLogger).splitOnDelimiter(parameterStyleEntry.getValue(), parameterStyleEntry.getKey(), bodyParameterEntry.getValues()));
                        bodyParameters.replaceEntry(bodyParameterEntry);
                    }
                }
            }
        }
    }

    @SuppressWarnings({"unchecked"})
    private Object xmlToMap(Node node) {
        Map<String, Object> objectMap = new HashMap<>();
        NodeList childNodes = node.getChildNodes();
        JsonNode content = null;
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node item = childNodes.item(i);
            if (item.getChildNodes().getLength() > 0) {
                if (objectMap.containsKey(item.getNodeName())) {
                    Object object = objectMap.get(item.getNodeName());
                    if (object instanceof List) {
                        ((List<Object>) object).add(xmlToMap(item));
                    } else if (object != null) {
                        List<Object> list = new ArrayList<>();
                        list.add(object);
                        list.add(xmlToMap(item));
                        objectMap.put(item.getNodeName(), list);
                    }
                } else {
                    objectMap.put(item.getNodeName(), xmlToMap(item));
                }
            } else if (item.getNodeType() == Node.TEXT_NODE) {
                content = toJsonObject(item.getTextContent().trim());
            }
        }
        return objectMap.size() > 0 ? objectMap : content;
    }

    private static JsonNode toJsonObject(final Collection<String> values) {
        if (values.size() == 0) {
            return NullNode.getInstance();
        }
        if (values.size() == 1) {
            return toJsonObject(values.iterator().next());
        }
        return new ArrayNode(
            JsonNodeFactory.instance,
            values.stream().map(JsonSchemaBodyDecoder::toJsonObject).collect(toList())
        );
    }

    private static JsonNode toJsonObject(@Nullable final String value) {
        if (value == null || value.equalsIgnoreCase("null")) {
            return NullNode.getInstance();
        }
        final String trimmed = value.trim();
        if (trimmed.equalsIgnoreCase("false")) {
            return BooleanNode.getFalse();
        }
        if (trimmed.equalsIgnoreCase("true")) {
            return BooleanNode.getTrue();
        }
        try {
            return new LongNode(parseLong(trimmed));
        } catch (final NumberFormatException ignore) {
        }
        try {
            return new DoubleNode(parseDouble(trimmed));
        } catch (final NumberFormatException ignore) {
        }
        return new TextNode(trimmed);
    }
}
