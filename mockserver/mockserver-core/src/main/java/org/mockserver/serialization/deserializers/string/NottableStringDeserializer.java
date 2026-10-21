package org.mockserver.serialization.deserializers.string;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import org.mockserver.matchers.DateTimeComparisonMatcher.Operator;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.NottableString;
import org.mockserver.model.ParameterStyle;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.mockserver.model.NottableOptionalString.optional;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringDeserializer extends StdDeserializer<NottableString> {

    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public NottableStringDeserializer() {
        super(NottableString.class);
    }

    @Override
    public NottableString deserialize(JsonParser jsonParser, DeserializationContext ctxt) throws IOException {
        if (jsonParser.getCurrentToken() == JsonToken.START_OBJECT) {
            Boolean not = null;
            Boolean optional = null;
            String value = null;
            JsonNode schema = null;
            ParameterStyle parameterStyle = null;
            String schemaType = null;
            Operator dateTimeOperator = null;
            String expected = null;
            String actualFormat = null;
            String truncateExpected = null;
            String truncateActual = null;
            Boolean applyTruncationLast = null;
            String zone = null;

            while (jsonParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldName = jsonParser.currentName();
                if ("not".equals(fieldName)) {
                    jsonParser.nextToken();
                    not = jsonParser.getBooleanValue();
                } else if ("optional".equals(fieldName)) {
                    jsonParser.nextToken();
                    optional = jsonParser.getBooleanValue();
                } else if ("value".equals(fieldName)) {
                    jsonParser.nextToken();
                    value = ctxt.readValue(jsonParser, String.class);
                } else if ("schema".equals(fieldName)) {
                    jsonParser.nextToken();
                    schema = ctxt.readValue(jsonParser, JsonNode.class);
                } else if ("parameterStyle".equals(fieldName)) {
                    jsonParser.nextToken();
                    parameterStyle = ctxt.readValue(jsonParser, ParameterStyle.class);
                } else if ("schemaType".equals(fieldName)) {
                    jsonParser.nextToken();
                    schemaType = ctxt.readValue(jsonParser, String.class);
                } else if (Operator.forFieldName(fieldName) != null) {
                    if (dateTimeOperator != null) {
                        throw new IllegalArgumentException("a date-time matcher has only one of before, after or equalToDateTime");
                    }
                    dateTimeOperator = Operator.forFieldName(fieldName);
                    jsonParser.nextToken();
                    expected = ctxt.readValue(jsonParser, String.class);
                } else if ("actualFormat".equals(fieldName)) {
                    jsonParser.nextToken();
                    actualFormat = ctxt.readValue(jsonParser, String.class);
                } else if ("truncateExpected".equals(fieldName)) {
                    jsonParser.nextToken();
                    truncateExpected = ctxt.readValue(jsonParser, String.class);
                } else if ("truncateActual".equals(fieldName)) {
                    jsonParser.nextToken();
                    truncateActual = ctxt.readValue(jsonParser, String.class);
                } else if ("applyTruncationLast".equals(fieldName)) {
                    jsonParser.nextToken();
                    applyTruncationLast = jsonParser.getBooleanValue();
                } else if ("zone".equals(fieldName)) {
                    jsonParser.nextToken();
                    zone = ctxt.readValue(jsonParser, String.class);
                }
            }

            NottableString result = null;
            if (dateTimeOperator != null) {
                if (schema != null || value != null || Boolean.TRUE.equals(optional)) {
                    throw new IllegalArgumentException("a date-time matcher cannot also have value, schema or optional");
                }
                result = NottableDateTimeString.dateTime(dateTimeOperator, expected, actualFormat, truncateExpected, truncateActual, applyTruncationLast, zone, not);
            } else if (actualFormat != null || truncateExpected != null || truncateActual != null || applyTruncationLast != null || zone != null) {
                throw new IllegalArgumentException("actualFormat, truncateExpected, truncateActual, applyTruncationLast and zone need one of before, after or equalToDateTime");
            } else if (schema != null) {
                result = schemaString(schema.toPrettyString(), not);
            } else if (Boolean.TRUE.equals(optional)) {
                result = optional(value, not);
            } else if (isNotBlank(value)) {
                result = string(value, not);
            }

            if (result != null && parameterStyle != null) {
                result = result.withStyle(parameterStyle);
            }
            if (result != null && schemaType != null) {
                result = result.withSchemaType(schemaType);
            }

            return result;
        } else if (jsonParser.getCurrentToken() == JsonToken.VALUE_STRING || jsonParser.getCurrentToken() == JsonToken.FIELD_NAME) {
            return string(ctxt.readValue(jsonParser, String.class));
        }
        return null;
    }

}
