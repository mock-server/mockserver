package org.mockserver.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.github.fge.jsonschema.main.JsonValidator;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.parser.util.SchemaTypeUtil;
import org.apache.commons.lang3.StringUtils;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.serialization.ObjectMapperFactory;
import org.slf4j.event.Level;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;

import static io.swagger.v3.parser.util.SchemaTypeUtil.*;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class NottableSchemaString extends NottableString {

    private final static MockServerLogger MOCK_SERVER_LOGGER = new MockServerLogger(NottableSchemaString.class);
    private final static ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();
    private final static JsonValidator VALIDATOR = JsonSchemaFactory.byDefault().getValidator();
    private final static DateTimeFormatter RFC3339 = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd")
        .appendLiteral('T')
        .appendPattern("HH:mm:ss")
        .optionalStart()
        .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true).parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
        .optionalEnd()
        .appendOffset("+HH:mm", "Z").toFormatter();
    private static final String TRUE = "true";
    private final ObjectNode schemaJsonNode;
    private final String type;
    private final String format;
    private final String json;


    private static JsonNode convertToJsonNode(@Nonnull final String value, final String type, final String format) throws IOException {
        if ("null".equalsIgnoreCase(value)) {
            return OBJECT_MAPPER.readTree("null");
        }
        if (DATE_TIME_FORMAT.equalsIgnoreCase(format)) {
            String result;
            try {
                // reformat to RFC3339 version that avoid schema validator errors
                result = LocalDateTime.parse(value, RFC3339).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
            } catch (final DateTimeParseException e) {
                // not a valid RFC3339 format schema validator will throw correct error
                result = value;
            }
            return new TextNode(result);
        }
        if (STRING_TYPE.equalsIgnoreCase(type)) {
            return new TextNode(value);
        }
        if (NUMBER_TYPE.equalsIgnoreCase(type) ||
            INTEGER_TYPE.equalsIgnoreCase(type)) {
            try {
                // validate double format
                Double.parseDouble(value);
                return OBJECT_MAPPER.readTree(value);
            } catch (final NumberFormatException nfe) {
                return new TextNode(value);
            }
        }
        return OBJECT_MAPPER.readTree(value);
    }

    private ObjectNode getSchemaJsonNode(String schema) {
        try {
            ObjectNode jsonNodes = (ObjectNode) OBJECT_MAPPER.readTree(schema);
            // remove embedded not field (for nottable schema string support)
            jsonNodes.remove("not");
            return jsonNodes;
        } catch (Throwable throwable) {
            MOCK_SERVER_LOGGER.logEvent(
                new LogEntry()
                    .setLogLevel(Level.ERROR)
                    .setMessageFormat("exception loading JSON Schema " + throwable.getMessage())
                    .setThrowable(throwable)
            );
            return null;
        }
    }

    public static NottableSchemaString schemaString(String value, Boolean not) {
        return new NottableSchemaString(value, not);
    }

    public static NottableSchemaString schemaString(String value) {
        return new NottableSchemaString(value);
    }

    public static NottableSchemaString notSchema(String value) {
        return new NottableSchemaString(value, Boolean.TRUE);
    }

    private NottableSchemaString(String schema, Boolean not) {
        super(schema, not);
        if (isNotBlank(schema)) {
            schemaJsonNode = getSchemaJsonNode(getValue());
            Schema<?> schemaByType = SchemaTypeUtil.createSchemaByType(schemaJsonNode);
            type = schemaByType.getType();
            format = schemaByType.getFormat();
        } else {
            schemaJsonNode = null;
            type = null;
            format = null;
        }
        json = (Boolean.TRUE.equals(isNot()) ? NOT_CHAR : "") + schema;
    }

    private NottableSchemaString(String schema) {
        super(schema);
        if (isNotBlank(schema)) {
            schemaJsonNode = getSchemaJsonNode(getValue());
            Schema<?> schemaByType = SchemaTypeUtil.createSchemaByType(schemaJsonNode);
            type = schemaByType.getType();
            format = schemaByType.getFormat();
        } else {
            schemaJsonNode = null;
            type = null;
            format = null;
        }
        json = (Boolean.TRUE.equals(isNot()) ? NOT_CHAR : "") + schema;
    }

    public boolean matches(String json) {
        if (schemaJsonNode != null) {
            try {
                return isNot() != validate(json);
            } catch (Throwable throwable) {
                MOCK_SERVER_LOGGER.logEvent(
                    new LogEntry()
                        .setLogLevel(Level.ERROR)
                        .setMessageFormat("exception validating JSON")
                        .setThrowable(throwable)
                );
            }
            return isNot();
        } else {
            return !isNot();
        }
    }

    private boolean validate(String json) throws ProcessingException, IOException {
        if (schemaJsonNode.get("nullable") != null && TRUE.equals(schemaJsonNode.get("nullable").asText()) && StringUtils.isBlank(json)) {
            return true;
        } else {
            return VALIDATOR.validate(schemaJsonNode, convertToJsonNode(json, type, format), false).isSuccess();
        }
    }

    public boolean matchesIgnoreCase(String json) {
        return matches(json);
    }

    @Override
    public String toString() {
        return json;
    }
}
