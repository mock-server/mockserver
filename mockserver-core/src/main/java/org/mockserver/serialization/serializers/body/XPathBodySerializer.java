package org.mockserver.serialization.serializers.body;

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
    public void serialize(XPathBody xPathBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (xPathBody.getNot() != null && xPathBody.getNot()) {
            jgen.writeBooleanField("not", xPathBody.getNot());
        }
        jgen.writeStringField("type", xPathBody.getType().name());
        jgen.writeStringField("xpath", xPathBody.getValue());
        jgen.writeEndObject();
    }
}
