package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.XPathBodyDTO;

import java.io.IOException;
import java.util.Map;

/**
 * @author jamesdbloom
 */
public class XPathBodyDTOSerializer extends StdSerializer<XPathBodyDTO> {

    public XPathBodyDTOSerializer() {
        super(XPathBodyDTO.class);
    }

    @Override
    public void serialize(XPathBodyDTO xPathBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (xPathBodyDTO.getNot() != null && xPathBodyDTO.getNot()) {
            jgen.writeBooleanField("not", xPathBodyDTO.getNot());
        }
        if (xPathBodyDTO.getOptional() != null && xPathBodyDTO.getOptional()) {
            jgen.writeBooleanField("optional", xPathBodyDTO.getOptional());
        }
        jgen.writeStringField("type", xPathBodyDTO.getType().name());
        jgen.writeStringField("xpath", xPathBodyDTO.getXPath());

        if (xPathBodyDTO.getNamespacePrefixes() != null) {
          jgen.writeObjectFieldStart("namespacePrefixes");
          for (Map.Entry<String, String> entry : xPathBodyDTO.getNamespacePrefixes().entrySet()) {
            jgen.writeStringField(entry.getKey(), entry.getValue());
          }
          jgen.writeEndObject();
        }
        jgen.writeEndObject();
    }
}
