package org.mockserver.client.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.XmlBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class XmlBodyDTOSerializer extends StdSerializer<XmlBodyDTO> {

    public XmlBodyDTOSerializer() {
        super(XmlBodyDTO.class);
    }

    @Override
    public void serialize(XmlBodyDTO xmlBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (xmlBodyDTO.getNot() != null && xmlBodyDTO.getNot()) {
            jgen.writeBooleanField("not", xmlBodyDTO.getNot());
        }
        jgen.writeStringField("type", xmlBodyDTO.getType().name());
        jgen.writeStringField("xml", xmlBodyDTO.getXml());
        jgen.writeEndObject();
    }
}
