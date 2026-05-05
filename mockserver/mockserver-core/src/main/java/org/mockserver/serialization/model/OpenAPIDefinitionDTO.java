package org.mockserver.serialization.model;

import org.mockserver.model.OpenAPIDefinition;

public class OpenAPIDefinitionDTO extends RequestDefinitionDTO {

    private String specUrlOrPayload;
    private String operationId;

    public OpenAPIDefinitionDTO(OpenAPIDefinition openAPIMatcher) {
        super(openAPIMatcher != null ? openAPIMatcher.getNot() : null);
        if (openAPIMatcher != null) {
            specUrlOrPayload = openAPIMatcher.getSpecUrlOrPayload();
            operationId = openAPIMatcher.getOperationId();
        }
    }

    public OpenAPIDefinitionDTO() {
        super(false);
    }

    public OpenAPIDefinition buildObject() {
        return (OpenAPIDefinition) new OpenAPIDefinition()
            .withSpecUrlOrPayload(specUrlOrPayload)
            .withOperationId(operationId)
            .withNot(getNot());
    }

    public String getSpecUrlOrPayload() {
        return specUrlOrPayload;
    }

    public OpenAPIDefinitionDTO setSpecUrlOrPayload(String specUrlOrPayload) {
        this.specUrlOrPayload = specUrlOrPayload;
        return this;
    }

    public String getOperationId() {
        return operationId;
    }

    public OpenAPIDefinitionDTO setOperationId(String operationId) {
        this.operationId = operationId;
        return this;
    }
}
