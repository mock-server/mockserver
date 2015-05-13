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
        boolean charsetFieldSetAndNonDefault = stringBody.getCharset() != null && !stringBody.getCharset().equals(ContentTypeMapper.DEFAULT_HTTP_CHARACTER_SET);
        if (notFieldSetAndNonDefault || charsetFieldSetAndNonDefault) {
            jgen.writeStartObject();
            if (notFieldSetAndNonDefault) {
                jgen.writeBooleanField("not", true);
            }
            if (charsetFieldSetAndNonDefault) {
                jgen.writeStringField("charset", stringBody.getCharset().name());
            }
            jgen.writeStringField("type", stringBody.getType().name());
            jgen.writeStringField("string", stringBody.getValue());
            jgen.writeEndObject();
        } else {
            jgen.writeString(stringBody.getValue());
        }
    }
}
