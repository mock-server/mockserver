package org.mockserver.model;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Objects;

public class CrudExpectationsDefinition {

    private String basePath;
    private String idField = "id";
    private IdStrategy idStrategy = IdStrategy.AUTO_INCREMENT;
    private List<ObjectNode> initialData;

    public enum IdStrategy {
        AUTO_INCREMENT,
        UUID
    }

    public String getBasePath() {
        return basePath;
    }

    public CrudExpectationsDefinition withBasePath(String basePath) {
        this.basePath = basePath;
        return this;
    }

    public String getIdField() {
        return idField;
    }

    public CrudExpectationsDefinition withIdField(String idField) {
        this.idField = idField;
        return this;
    }

    public IdStrategy getIdStrategy() {
        return idStrategy;
    }

    public CrudExpectationsDefinition withIdStrategy(IdStrategy idStrategy) {
        this.idStrategy = idStrategy;
        return this;
    }

    public List<ObjectNode> getInitialData() {
        return initialData;
    }

    public CrudExpectationsDefinition withInitialData(List<ObjectNode> initialData) {
        this.initialData = initialData;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        CrudExpectationsDefinition that = (CrudExpectationsDefinition) o;
        return Objects.equals(basePath, that.basePath)
            && Objects.equals(idField, that.idField)
            && idStrategy == that.idStrategy
            && Objects.equals(initialData, that.initialData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(basePath, idField, idStrategy, initialData);
    }
}
