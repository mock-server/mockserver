package org.mockserver.serialization.serializers.expectation;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.serialization.ObjectMapperFactory;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectationSerializer extends StdSerializer<OpenAPIExpectation> {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperFactory.createObjectMapper();

    public OpenAPIExpectationSerializer() {
        super(OpenAPIExpectation.class);
    }

    @Override
    public void serialize(OpenAPIExpectation openAPIDefinition, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (isNotBlank(openAPIDefinition.getSpecUrlOrPayload())) {
            if (openAPIDefinition.getSpecUrlOrPayload().trim().startsWith("{")) {
                jgen.writeObjectField("specUrlOrPayload", OBJECT_MAPPER.readTree(openAPIDefinition.getSpecUrlOrPayload()));
            } else {
                jgen.writeObjectField("specUrlOrPayload", openAPIDefinition.getSpecUrlOrPayload());
            }
        }
        if (openAPIDefinition.getOperationsAndResponses() != null && !openAPIDefinition.getOperationsAndResponses().isEmpty()) {
            jgen.writeObjectField("operationsAndResponses", openAPIDefinition.getOperationsAndResponses());
        }
        jgen.writeEndObject();
    }
}
