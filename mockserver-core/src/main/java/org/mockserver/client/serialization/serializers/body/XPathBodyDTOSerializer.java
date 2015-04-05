package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.XPathBodyDTO;

import java.io.IOException;

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
        jgen.writeStringField("type", xPathBodyDTO.getType().name());
        jgen.writeStringField("xpath", xPathBodyDTO.getXPath());
        jgen.writeEndObject();
    }
}
