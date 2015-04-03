package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.XPathBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class XPathBodySerializer extends StdSerializer<XPathBody> {

    public XPathBodySerializer() {
        super(XPathBody.class);
    }

    @Override
    public void serialize(XPathBody xPathBody, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        json.writeStringField("type", xPathBody.getType().name());
        json.writeStringField("value", xPathBody.getValue());
        json.writeEndObject();
    }
}
