package org.mockserver.serialization.serializers.body;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import org.mockserver.model.GraphQLBody;

import java.io.IOException;

public class GraphQLBodySerializer extends StdSerializer<GraphQLBody> {

    private static final long serialVersionUID = 1L;

    public GraphQLBodySerializer() {
        super(GraphQLBody.class);
    }

    @Override
    public void serialize(GraphQLBody graphQLBody, JsonGenerator jgen, SerializerProvider provider) throws IOException {
        jgen.writeStartObject();
        if (graphQLBody.getNot() != null && graphQLBody.getNot()) {
            jgen.writeBooleanField("not", graphQLBody.getNot());
        }
        if (graphQLBody.getOptional() != null && graphQLBody.getOptional()) {
            jgen.writeBooleanField("optional", graphQLBody.getOptional());
        }
        jgen.writeStringField("type", graphQLBody.getType().name());
        jgen.writeStringField("query", graphQLBody.getQuery());
        if (graphQLBody.getOperationName() != null) {
            jgen.writeStringField("operationName", graphQLBody.getOperationName());
        }
        if (graphQLBody.getVariablesSchema() != null) {
            jgen.writeStringField("variablesSchema", graphQLBody.getVariablesSchema());
        }
        jgen.writeEndObject();
    }
}
