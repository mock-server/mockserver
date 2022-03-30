package org.mockserver.mock;

import org.mockserver.model.ObjectWithJsonToString;

import java.util.Map;
import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class OpenAPIExpectation extends ObjectWithJsonToString {

    private int hashCode;
    private String specUrlOrPayload;
    private Map<String, String> operationsAndResponses;

    public static OpenAPIExpectation openAPIExpectation() {
        return new OpenAPIExpectation();
    }

    /**
     * Specify the OpenAPI and operations and responses to create matchers and example responses for:
     *
     * <p>
     * <pre>
     * // Create from a publicly hosted HTTP location (json or yaml)
     * openAPIExpectation("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     *
     * // Create from a file on the local filesystem (json or yaml)
     * openAPIExpectation("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     *
     * // Create from a classpath resource in the /api package (json or yaml)
     * openAPIExpectation("org/mockserver/openapi/openapi_petstore_example.json", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     *
     * // Create from an OpenAPI payload (json or yaml)
     * openAPIExpectation("{\"openapi\": \"3.0.0\", \"info\": { ...", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     * </pre>
     *
     * @param specUrlOrPayload       the OpenAPI to match against by URL or payload
     * @param operationsAndResponses operations and responses to use for each example response where the key is the operationId in the OpenAPI and the value if the response key (i.e. "200", "400" or "default")
     * @return the OpenAPIExpectation
     */
    public static OpenAPIExpectation openAPIExpectation(String specUrlOrPayload, Map<String, String> operationsAndResponses) {
        return new OpenAPIExpectation()
            .withSpecUrlOrPayload(specUrlOrPayload)
            .withOperationsAndResponses(operationsAndResponses);
    }

    /**
     * Specify the OpenAPI to create matchers and example responses for:
     *
     * <p>
     * <pre>
     * // Create from a publicly hosted HTTP location (json or yaml)
     * openAPIExpectation("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     *
     * // Create from a file on the local filesystem (json or yaml)
     * openAPIExpectation("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     *
     * // Create from a classpath resource in the /api package (json or yaml)
     * openAPIExpectation("org/mockserver/openapi/openapi_petstore_example.json", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     *
     * // Create from an OpenAPI payload (json or yaml)
     * openAPIExpectation("{\"openapi\": \"3.0.0\", \"info\": { ...", ImmutableMap.of(
     *     "listPets", "500",
     *     "createPets", "default",
     *     "showPetById", "200"
     * ));
     * </pre>
     *
     * @param specUrlOrPayload the OpenAPI to match against by URL or payload
     * @return the OpenAPIExpectation
     */
    public static OpenAPIExpectation openAPIExpectation(String specUrlOrPayload) {
        return new OpenAPIExpectation()
            .withSpecUrlOrPayload(specUrlOrPayload);
    }

    public String getSpecUrlOrPayload() {
        return specUrlOrPayload;
    }

    /**
     * Specify the OpenAPI specification:
     *
     * <p>
     * <pre>
     * // Create from a publicly hosted HTTP location (json or yaml)
     * withSpecUrlOrPayload("https://raw.githubusercontent.com/OAI/OpenAPI-Specification/master/examples/v3.0/petstore-expanded.yaml");
     *
     * // Create from a file on the local filesystem (json or yaml)
     * withSpecUrlOrPayload("file://Users/myuser/git/mockserver/mockserver-core/src/test/resources/org/mockserver/openapi/openapi_petstore_example.json");
     *
     * // Create from a classpath resource in the /api package (json or yaml)
     * withSpecUrlOrPayload("org/mockserver/openapi/openapi_petstore_example.json");
     *
     * // Create from an OpenAPI payload (json or yaml)
     * withSpecUrlOrPayload("{\"openapi\": \"3.0.0\", \"info\": { ...");
     * </pre>
     *
     * @param specUrlOrPayload the OpenAPI to match against by URL or payload
     * @return this OpenAPIExpectation
     */
    public OpenAPIExpectation withSpecUrlOrPayload(String specUrlOrPayload) {
        this.specUrlOrPayload = specUrlOrPayload;
        return this;
    }

    public Map<String, String> getOperationsAndResponses() {
        return operationsAndResponses;
    }

    /**
     * The operations and responses to use for each example response where the key is the operationId in the OpenAPI and the value if the response key (i.e. "200", "400" or "default")
     *
     * @param operationsAndResponses operations and responses to use for each example response
     * @return this OpenAPIExpectation
     */
    public OpenAPIExpectation withOperationsAndResponses(Map<String, String> operationsAndResponses) {
        this.operationsAndResponses = operationsAndResponses;
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
        if (hashCode() != o.hashCode()) {
            return false;
        }
        OpenAPIExpectation that = (OpenAPIExpectation) o;
        return Objects.equals(specUrlOrPayload, that.specUrlOrPayload) &&
            Objects.equals(operationsAndResponses, that.operationsAndResponses);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(specUrlOrPayload, operationsAndResponses);
        }
        return hashCode;
    }
}
