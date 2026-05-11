package org.mockserver.model;

import java.util.Objects;

public class GraphQLBody extends Body<String> {
    private int hashCode;
    private final String query;
    private final String operationName;
    private final String variablesSchema;

    public GraphQLBody(String query) {
        this(query, null, null);
    }

    public GraphQLBody(String query, String operationName, String variablesSchema) {
        super(Type.GRAPHQL);
        this.query = query;
        this.operationName = operationName;
        this.variablesSchema = variablesSchema;
    }

    public static GraphQLBody graphQL(String query) {
        return new GraphQLBody(query);
    }

    public static GraphQLBody graphQL(String query, String operationName) {
        return new GraphQLBody(query, operationName, null);
    }

    public static GraphQLBody graphQL(String query, String operationName, String variablesSchema) {
        return new GraphQLBody(query, operationName, variablesSchema);
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

    @Override
    public String getValue() {
        return query;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        GraphQLBody that = (GraphQLBody) o;
        return Objects.equals(query, that.query) &&
            Objects.equals(operationName, that.operationName) &&
            Objects.equals(variablesSchema, that.variablesSchema);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(super.hashCode(), query, operationName, variablesSchema);
        }
        return hashCode;
    }

    @Override
    public String toString() {
        return query;
    }
}
