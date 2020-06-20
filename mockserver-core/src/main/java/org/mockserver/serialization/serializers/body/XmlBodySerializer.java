package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.XmlBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class XmlBodySerializer extends StdSerializer<XmlBody> {

    public XmlBodySerializer() {
        super(XmlBody.class);
    }

    @Override
    public void serialize(XmlBody xmlBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (xmlBody.getNot() != null && xmlBody.getNot()) {
            jgen.writeBooleanField("not", xmlBody.getNot());
        }
        if (xmlBody.getContentType() != null && !xmlBody.getContentType().equals(XmlBody.DEFAULT_XML_CONTENT_TYPE.toString())) {
            jgen.writeStringField("contentType", xmlBody.getContentType());
        }
        jgen.writeStringField("type", xmlBody.getType().name());
        jgen.writeStringField("xml", xmlBody.getValue());
        jgen.writeEndObject();
    }
}
