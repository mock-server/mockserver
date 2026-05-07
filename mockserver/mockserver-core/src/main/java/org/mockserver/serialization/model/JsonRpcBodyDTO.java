package org.mockserver.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.JsonRpcBody;

public class JsonRpcBodyDTO extends BodyDTO {

    private final String method;
    private final String paramsSchema;

    public JsonRpcBodyDTO(JsonRpcBody jsonRpcBody) {
        this(jsonRpcBody, null);
    }

    public JsonRpcBodyDTO(JsonRpcBody jsonRpcBody, Boolean not) {
        super(Body.Type.JSON_RPC, not);
        this.method = jsonRpcBody.getMethod();
        this.paramsSchema = jsonRpcBody.getParamsSchema();
        withOptional(jsonRpcBody.getOptional());
    }

    public String getMethod() {
        return method;
    }

    public String getParamsSchema() {
        return paramsSchema;
    }

    public JsonRpcBody buildObject() {
        return (JsonRpcBody) new JsonRpcBody(getMethod(), getParamsSchema()).withOptional(getOptional());
    }
}
