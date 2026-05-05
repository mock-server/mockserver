package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.XPathBody;

import java.io.IOException;
import java.util.Map;

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
        if (xPathBody.getOptional() != null && xPathBody.getOptional()) {
            jgen.writeBooleanField("optional", xPathBody.getOptional());
        }
        jgen.writeStringField("type", xPathBody.getType().name());
        jgen.writeStringField("xpath", xPathBody.getValue());
        if (xPathBody.getNamespacePrefixes() != null) {
          jgen.writeObjectFieldStart("namespacePrefixes");
          for (Map.Entry<String, String> entry : xPathBody.getNamespacePrefixes().entrySet()) {
            jgen.writeStringField(entry.getKey(), entry.getValue());
          }
          jgen.writeEndObject();
        }

        jgen.writeEndObject();
    }
}
