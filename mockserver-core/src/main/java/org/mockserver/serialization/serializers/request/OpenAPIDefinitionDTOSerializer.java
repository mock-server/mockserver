package org.mockserver.serialization.serializers.request;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.OpenAPIDefinitionDTO;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class OpenAPIDefinitionDTOSerializer extends StdSerializer<OpenAPIDefinitionDTO> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public OpenAPIDefinitionDTOSerializer() {
        super(OpenAPIDefinitionDTO.class);
    }

    @Override
    public void serialize(OpenAPIDefinitionDTO openAPIDefinition, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (openAPIDefinition.getNot() != null && openAPIDefinition.getNot()) {
            jgen.writeBooleanField("not", openAPIDefinition.getNot());
        }
        if (isNotBlank(openAPIDefinition.getSpecUrlOrPayload())) {
            if (openAPIDefinition.getSpecUrlOrPayload().trim().startsWith("{")) {
                jgen.writeObjectField("specUrlOrPayload", OBJECT_MAPPER.readTree(openAPIDefinition.getSpecUrlOrPayload()));
            } else {
                jgen.writeObjectField("specUrlOrPayload", openAPIDefinition.getSpecUrlOrPayload());
            }
        }
        if (isNotBlank(openAPIDefinition.getOperationId())) {
            jgen.writeObjectField("operationId", openAPIDefinition.getOperationId());
        }
        jgen.writeEndObject();
    }
}
