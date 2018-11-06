package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.RegexBodyDTO;

import java.io.IOException;

/**
 * @author jamesdbloom
 */
public class RegexBodyDTOSerializer extends StdSerializer<RegexBodyDTO> {

    public RegexBodyDTOSerializer() {
        super(RegexBodyDTO.class);
    }

    @Override
    public void serialize(RegexBodyDTO regexBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (regexBodyDTO.getNot() != null && regexBodyDTO.getNot()) {
            jgen.writeBooleanField("not", regexBodyDTO.getNot());
        }
        jgen.writeStringField("type", regexBodyDTO.getType().name());
        jgen.writeStringField("regex", regexBodyDTO.getRegex());
        jgen.writeEndObject();
    }
}
