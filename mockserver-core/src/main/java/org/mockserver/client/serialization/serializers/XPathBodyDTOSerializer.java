package org.mockserver.client.serialization.serializers;

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
    public void serialize(XPathBodyDTO xPathBodyDTO, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        if (xPathBodyDTO.getNot() != null && xPathBodyDTO.getNot()) {
            json.writeBooleanField("not", xPathBodyDTO.getNot());
        }
        json.writeStringField("type", xPathBodyDTO.getType().name());
        json.writeStringField("value", xPathBodyDTO.getXPath());
        json.writeEndObject();
    }
}
