package org.mockserver.serialization.model;

import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.model.ObjectWithJsonToString;

import java.util.Map;

public class OpenAPIExpectationDTO extends ObjectWithJsonToString {
    private String specUrlOrPayload;
    private Map<String, Object> operationsAndResponses;
    private String contextPathPrefix;

    public OpenAPIExpectationDTO(OpenAPIExpectation openAPIExpectation) {
        if (openAPIExpectation != null) {
            specUrlOrPayload = openAPIExpectation.getSpecUrlOrPayload();
            operationsAndResponses = openAPIExpectation.getOperationsAndResponses();
            contextPathPrefix = openAPIExpectation.getContextPathPrefix();
        }
    }

    public OpenAPIExpectationDTO() {
    }

    public OpenAPIExpectation buildObject() {
        return new OpenAPIExpectation()
            .withSpecUrlOrPayload(specUrlOrPayload)
            .withOperationsAndResponses(operationsAndResponses)
            .withContextPathPrefix(contextPathPrefix);
    }

    public String getSpecUrlOrPayload() {
        return specUrlOrPayload;
    }

    public OpenAPIExpectationDTO setSpecUrlOrPayload(String specUrlOrPayload) {
        this.specUrlOrPayload = specUrlOrPayload;
        return this;
    }

    public Map<String, Object> getOperationsAndResponses() {
        return operationsAndResponses;
    }

    public OpenAPIExpectationDTO setOperationsAndResponses(Map<String, Object> operationsAndResponses) {
        this.operationsAndResponses = operationsAndResponses;
        return this;
    }

    public String getContextPathPrefix() {
        return contextPathPrefix;
    }

    public OpenAPIExpectationDTO setContextPathPrefix(String contextPathPrefix) {
        this.contextPathPrefix = contextPathPrefix;
        return this;
    }

}
