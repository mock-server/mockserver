package org.mockserver.serialization.deserializers.body;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.mockserver.model.Body;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Map;

/**
 * Reads the {@code dateTime} field of a JSONPath or XPath body matcher: the same object form as a
 * date-time header or query value, such as {@code {"after": "now -1 days"}}.
 */
final class BodyDateTimeField {

    static final String FIELD_NAME = "dateTime";

    private BodyDateTimeField() {
    }

    /**
     * @throws IllegalArgumentException saying what is wrong
     */
    private static NottableDateTimeString read(ObjectMapper objectMapper, Object value) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map)) {
            throw new IllegalArgumentException("a body dateTime is an object such as {\"after\": \"now -1 days\"}");
        }
        NottableString parsed = objectMapper.convertValue(value, NottableString.class);
        if (!(parsed instanceof NottableDateTimeString)) {
            throw new IllegalArgumentException("a body dateTime needs one of before, after or equalToDateTime");
        }
        return (NottableDateTimeString) parsed;
    }

    /**
     * The date-time comparison of a JSON_PATH or XPATH body. On a body without an explicit {@code type},
     * such as the untyped JSON shorthand {@code {"id": 1, "dateTime": "..."}}, the field is body content
     * and is left alone; on an explicit other type it is rejected rather than silently ignored.
     *
     * @param objectMapper null to create one only when the field has to be read
     * @throws IllegalArgumentException saying what is wrong
     */
    static NottableDateTimeString forBody(ObjectMapper objectMapper, Object value, Body.Type type, boolean explicitType) {
        if (value == null) {
            return null;
        }
        if (type == Body.Type.JSON_PATH || type == Body.Type.XPATH) {
            return read(objectMapper != null ? objectMapper : ObjectMapperFactory.createObjectMapper(), value);
        }
        if (explicitType) {
            throw new IllegalArgumentException("a body dateTime applies only to JSON_PATH and XPATH bodies, not " + type);
        }
        return null;
    }
}
