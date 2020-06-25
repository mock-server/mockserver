package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.StringBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class StringBodySerializer extends StdSerializer<StringBody> {

    public StringBodySerializer() {
        super(StringBody.class);
    }

    @Override
    public void serialize(StringBody stringBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        boolean notFieldSetAndNotDefault = stringBody.getNot() != null && stringBody.getNot();
        boolean optionalFieldSetAndNotDefault = stringBody.getOptional() != null && stringBody.getOptional();
        boolean subStringFieldNotDefault = stringBody.isSubString();
        boolean contentTypeFieldSet = stringBody.getContentType() != null;
        if (notFieldSetAndNotDefault || optionalFieldSetAndNotDefault || contentTypeFieldSet || subStringFieldNotDefault) {
            jgen.writeStartObject();
            if (notFieldSetAndNotDefault) {
                jgen.writeBooleanField("not", true);
            }
            if (optionalFieldSetAndNotDefault) {
                jgen.writeBooleanField("optional", true);
            }
            jgen.writeStringField("type", stringBody.getType().name());
            jgen.writeStringField("string", stringBody.getValue());
            if (stringBody.getRawBytes() != null) {
                jgen.writeObjectField("rawBytes", stringBody.getRawBytes());
            }
            if (subStringFieldNotDefault) {
                jgen.writeBooleanField("subString", true);
            }
            if (contentTypeFieldSet) {
                jgen.writeStringField("contentType", stringBody.getContentType());
            }
            jgen.writeEndObject();
        } else {
            jgen.writeString(stringBody.getValue());
        }
    }
}
