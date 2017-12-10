package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.StringBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class StringBodyDTOSerializer extends StdSerializer<StringBodyDTO> {

    public StringBodyDTOSerializer() {
        super(StringBodyDTO.class);
    }

    @Override
    public void serialize(StringBodyDTO stringBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        boolean notFieldSetAndNonDefault = stringBodyDTO.getNot() != null && stringBodyDTO.getNot();
        boolean contentTypeFieldSet = stringBodyDTO.getContentType() != null;
        if (notFieldSetAndNonDefault || contentTypeFieldSet) {
            jgen.writeStartObject();
            if (notFieldSetAndNonDefault) {
                jgen.writeBooleanField("not", true);
            }
            jgen.writeStringField("type", stringBodyDTO.getType().name());
            jgen.writeStringField("string", stringBodyDTO.getString());
            if (contentTypeFieldSet) {
                jgen.writeStringField("contentType", stringBodyDTO.getContentType());
            }
            jgen.writeEndObject();
        } else {
            jgen.writeString(stringBodyDTO.getString());
        }
    }
}
