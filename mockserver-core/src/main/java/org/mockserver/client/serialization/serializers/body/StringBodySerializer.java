package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.mappers.ContentTypeMapper;
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
        boolean notFieldSetAndNonDefault = stringBody.getNot() != null && stringBody.getNot();
        boolean contentTypeFieldSet = stringBody.getContentType() != null;
        if (notFieldSetAndNonDefault || contentTypeFieldSet) {
            jgen.writeStartObject();
            if (notFieldSetAndNonDefault) {
                jgen.writeBooleanField("not", true);
            }
            if (contentTypeFieldSet) {
                jgen.writeStringField("contentType", stringBody.getContentType());
            }
            jgen.writeStringField("type", stringBody.getType().name());
            jgen.writeStringField("string", stringBody.getValue());
            jgen.writeEndObject();
        } else {
            jgen.writeString(stringBody.getValue());
        }
    }
}
