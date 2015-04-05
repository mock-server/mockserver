package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.RegexBody;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class RegexBodySerializer extends StdSerializer<RegexBody> {

    public RegexBodySerializer() {
        super(RegexBody.class);
    }

    @Override
    public void serialize(RegexBody regexBody, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        if (regexBody.isNot() != null && regexBody.isNot()) {
            json.writeBooleanField("not", regexBody.isNot());
        }
        json.writeStringField("type", regexBody.getType().name());
        json.writeStringField("value", regexBody.getValue());
        json.writeEndObject();
    }
}
