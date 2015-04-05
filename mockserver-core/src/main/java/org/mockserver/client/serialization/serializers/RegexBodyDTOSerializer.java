package org.mockserver.client.serialization.serializers;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.client.serialization.model.RegexBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class RegexBodyDTOSerializer extends StdSerializer<RegexBodyDTO> {

    public RegexBodyDTOSerializer() {
        super(RegexBodyDTO.class);
    }

    @Override
    public void serialize(RegexBodyDTO regexBodyDTO, JsonGenerator json, SerializerProvider provider) throws IOException {
        json.writeStartObject();
        if (regexBodyDTO.getNot() != null && regexBodyDTO.getNot()) {
            json.writeBooleanField("not", regexBodyDTO.getNot());
        }
        json.writeStringField("type", regexBodyDTO.getType().name());
        json.writeStringField("value", regexBodyDTO.getRegex());
        json.writeEndObject();
    }
}
