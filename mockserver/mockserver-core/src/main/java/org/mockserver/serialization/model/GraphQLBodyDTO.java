package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.GraphQLBody;

public class GraphQLBodyDTO extends BodyDTO {

    private final String query;
    private final String operationName;
    private final String variablesSchema;

    public GraphQLBodyDTO(GraphQLBody graphQLBody) {
        this(graphQLBody, null);
    }

    public GraphQLBodyDTO(GraphQLBody graphQLBody, Boolean not) {
        super(Body.Type.GRAPHQL, not);
        this.query = graphQLBody.getQuery();
        this.operationName = graphQLBody.getOperationName();
        this.variablesSchema = graphQLBody.getVariablesSchema();
        withOptional(graphQLBody.getOptional());
    }

    public String getQuery() {
        return query;
    }

    public String getOperationName() {
        return operationName;
    }

    public String getVariablesSchema() {
        return variablesSchema;
    }

    public GraphQLBody buildObject() {
        return (GraphQLBody) new GraphQLBody(getQuery(), getOperationName(), getVariablesSchema()).withOptional(getOptional());
    }
}
