package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.serialization.model.GraphQLBodyDTO;

import java.io.IOException;

public class GraphQLBodyDTOSerializer extends StdSerializer<GraphQLBodyDTO> {

    private static final long serialVersionUID = 1L;

    public GraphQLBodyDTOSerializer() {
        super(GraphQLBodyDTO.class);
    }

    @Override
    public void serialize(GraphQLBodyDTO graphQLBodyDTO, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (graphQLBodyDTO.getNot() != null && graphQLBodyDTO.getNot()) {
            jgen.writeBooleanField("not", graphQLBodyDTO.getNot());
        }
        if (graphQLBodyDTO.getOptional() != null && graphQLBodyDTO.getOptional()) {
            jgen.writeBooleanField("optional", graphQLBodyDTO.getOptional());
        }
        jgen.writeStringField("type", graphQLBodyDTO.getType().name());
        jgen.writeStringField("query", graphQLBodyDTO.getQuery());
        if (graphQLBodyDTO.getOperationName() != null) {
            jgen.writeStringField("operationName", graphQLBodyDTO.getOperationName());
        }
        if (graphQLBodyDTO.getVariablesSchema() != null) {
            jgen.writeStringField("variablesSchema", graphQLBodyDTO.getVariablesSchema());
        }
        jgen.writeEndObject();
    }
}
