package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.BinaryBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class BinaryBodyDTOSerializer extends StdSerializer<BinaryBodyDTO> {

    public BinaryBodyDTOSerializer() {
        super(BinaryBodyDTO.class);
    }

    @Override
    public void serialize(BinaryBodyDTO binaryBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (binaryBodyDTO.getNot() != null && binaryBodyDTO.getNot()) {
            jgen.writeBooleanField("not", binaryBodyDTO.getNot());
        }
        jgen.writeStringField("type", binaryBodyDTO.getType().name());
        jgen.writeObjectField("base64Bytes", binaryBodyDTO.getBase64Bytes());
        if (binaryBodyDTO.getContentType() != null) {
            jgen.writeStringField("contentType", binaryBodyDTO.getContentType());
        }
        jgen.writeEndObject();
    }
}
