package org.mockserver.serialization.model;

import org.mockserver.model.OpenAPIDefinition;

public class OpenAPIDefinitionDTO extends RequestDefinitionDTO {

    private String specUrlOrPayload;
    private String operationId;
    private String contextPathPrefix;

    public OpenAPIDefinitionDTO(OpenAPIDefinition openAPIMatcher) {
        super(openAPIMatcher != null ? openAPIMatcher.getNot() : null);
        if (openAPIMatcher != null) {
            specUrlOrPayload = openAPIMatcher.getSpecUrlOrPayload();
            operationId = openAPIMatcher.getOperationId();
            contextPathPrefix = openAPIMatcher.getContextPathPrefix();
        }
    }

    public OpenAPIDefinitionDTO() {
        super(false);
    }

    public OpenAPIDefinition buildObject() {
        return (OpenAPIDefinition) new OpenAPIDefinition()
            .withSpecUrlOrPayload(specUrlOrPayload)
            .withOperationId(operationId)
            .withContextPathPrefix(contextPathPrefix)
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

    public String getContextPathPrefix() {
        return contextPathPrefix;
    }

    public OpenAPIDefinitionDTO setContextPathPrefix(String contextPathPrefix) {
        this.contextPathPrefix = contextPathPrefix;
        return this;
    }
}
