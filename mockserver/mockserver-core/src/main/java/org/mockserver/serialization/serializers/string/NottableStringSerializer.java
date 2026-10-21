package org.mockserver.serialization.serializers.string;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.NottableDateTimeString;
import org.mockserver.model.NottableSchemaString;
import org.mockserver.model.NottableString;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;
import java.util.Objects;

import static org.mockserver.model.NottableString.serialiseNottableString;

/**
 * @author jamesdbloom
 */
public class NottableStringSerializer extends StdSerializer<NottableString> {
    private static final long serialVersionUID = 1L;

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public NottableStringSerializer() {
        super(NottableString.class);
    }

    @Override
    public void serialize(NottableString nottableString, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        if (nottableString instanceof NottableDateTimeString) {
            writeDateTime((NottableDateTimeString) nottableString, jgen);
        } else if (nottableString instanceof NottableSchemaString) {
            writeObject(nottableString, jgen, "schema", OBJECT_MAPPER.readTree(nottableString.getValue()), false);
        } else if (nottableString.getParameterStyle() != null) {
            writeObject(nottableString, jgen, "value", nottableString.getValue(), false);
        } else if (isAmbiguousAsPlainString(nottableString)) {
            // The plain-string form prefixes '!' for negation and '?' for optional, and the reader
            // strips those markers unconditionally. So a value whose own first character is one of
            // those markers cannot be represented as a bare string — "path IS !foo" would be read
            // back as "path is NOT foo", the exact opposite of what was asked. Fall back to the
            // object form, whose `value` field the deserialiser reads verbatim.
            writeObject(nottableString, jgen, "value", nottableString.getValue(), true);
        } else {
            jgen.writeString(serialiseNottableString(nottableString));
        }
    }

    /**
     * True when re-reading the plain-string form would not reproduce this value, negation and
     * optionality. Determined by actually re-parsing rather than by enumerating prefixes, so it
     * stays correct for compound markers ("?!", "!?") and any future marker character.
     * <p>
     * Public because the same question has to be asked of collection KEYS: a header, query
     * parameter or cookie NAME goes on the wire as a JSON field name, which has no object form,
     * so {@link org.mockserver.serialization.serializers.collections.KeysToMultiValuesSerializer}
     * uses this to decide when the whole collection must fall back to the array form.
     */
    public static boolean isAmbiguousAsPlainString(NottableString nottableString) {
        if (nottableString.isBlank()) {
            // a blank/null value serialises to "" and has no marker to misread; it also cannot be
            // expressed in the object form, whose deserialiser ignores a blank `value`
            return false;
        }
        NottableString reparsed = NottableString.string(serialiseNottableString(nottableString));
        return reparsed.isNot() != nottableString.isNot()
            || reparsed.isOptional() != nottableString.isOptional()
            || !Objects.equals(reparsed.getValue(), nottableString.getValue());
    }

    private void writeDateTime(NottableDateTimeString dateTime, JsonGenerator jgen) throws IOException {
        jgen.writeStartObject();
        if (Boolean.TRUE.equals(dateTime.isNot())) {
            jgen.writeBooleanField("not", true);
        }
        if (dateTime.getParameterStyle() != null) {
            jgen.writeObjectField("parameterStyle", dateTime.getParameterStyle());
        }
        if (dateTime.getSchemaType() != null) {
            jgen.writeStringField("schemaType", dateTime.getSchemaType());
        }
        jgen.writeStringField(dateTime.getOperator().fieldName(), dateTime.getExpected());
        writeIfPresent(jgen, "actualFormat", dateTime.getActualFormat());
        writeIfPresent(jgen, "truncateExpected", dateTime.getTruncateExpected());
        writeIfPresent(jgen, "truncateActual", dateTime.getTruncateActual());
        if (dateTime.getApplyTruncationLast() != null) {
            jgen.writeBooleanField("applyTruncationLast", dateTime.getApplyTruncationLast());
        }
        writeIfPresent(jgen, "zone", dateTime.getZone());
        jgen.writeEndObject();
    }

    private static void writeIfPresent(JsonGenerator jgen, String name, String value) throws IOException {
        if (value != null) {
            jgen.writeStringField(name, value);
        }
    }

    private void writeObject(NottableString nottableString, JsonGenerator jgen, String valueFieldName, Object value, boolean alwaysWriteNot) throws IOException {
        jgen.writeStartObject();
        if (alwaysWriteNot || Boolean.TRUE.equals(nottableString.isNot())) {
            // written explicitly when disambiguating so the intent is unmistakable to readers and
            // to the seven non-Java clients, rather than relying on absent-means-false
            jgen.writeBooleanField("not", nottableString.isNot());
        }
        if (Boolean.TRUE.equals(nottableString.isOptional())) {
            jgen.writeBooleanField("optional", true);
        }
        if (nottableString.getParameterStyle() != null) {
            jgen.writeObjectField("parameterStyle", nottableString.getParameterStyle());
        }
        if (nottableString.getSchemaType() != null) {
            jgen.writeStringField("schemaType", nottableString.getSchemaType());
        }
        jgen.writeObjectField(valueFieldName, value);
        jgen.writeEndObject();
    }

}
