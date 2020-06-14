package org.mockserver.serialization.model;

import org.mockserver.mock.OpenAPIExpectation;
import org.mockserver.model.ObjectWithJsonToString;

import java.util.Map;

public class OpenAPIExpectationDTO extends ObjectWithJsonToString {
    private String specUrlOrPayload;
    private Map<String, String> operationsAndResponses;

    public OpenAPIExpectationDTO(OpenAPIExpectation openAPIExpectation) {
        if (openAPIExpectation != null) {
            specUrlOrPayload = openAPIExpectation.getSpecUrlOrPayload();
            operationsAndResponses = openAPIExpectation.getOperationsAndResponses();
        }
    }

    public OpenAPIExpectationDTO() {
    }

    public OpenAPIExpectation buildObject() {
        return new OpenAPIExpectation()
            .withSpecUrlOrPayload(specUrlOrPayload)
            .withOperationsAndResponses(operationsAndResponses);
    }

    public String getSpecUrlOrPayload() {
        return specUrlOrPayload;
    }

    public OpenAPIExpectationDTO setSpecUrlOrPayload(String specUrlOrPayload) {
        this.specUrlOrPayload = specUrlOrPayload;
        return this;
    }

    public Map<String, String> getOperationsAndResponses() {
        return operationsAndResponses;
    }

    public OpenAPIExpectationDTO setOperationsAndResponses(Map<String, String> operationsAndResponses) {
        this.operationsAndResponses = operationsAndResponses;
        return this;
    }

}
