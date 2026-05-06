package org.mockserver.netty.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class JsonRpcMessage {

    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcRequest {
        private String jsonrpc;
        private String method;
        private JsonNode params;
        private Object id;
        private boolean idPresent;

        public String getJsonrpc() {
            return jsonrpc;
        }

        public void setJsonrpc(String jsonrpc) {
            this.jsonrpc = jsonrpc;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public JsonNode getParams() {
            return params;
        }

        public void setParams(JsonNode params) {
            this.params = params;
        }

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

        public boolean isIdPresent() {
            return idPresent;
        }

        public void setIdPresent(boolean idPresent) {
            this.idPresent = idPresent;
        }

        public boolean isNotification() {
            return !idPresent;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcResponse {
        private String jsonrpc = "2.0";
        private JsonNode result;
        private JsonRpcError error;
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Object id;

        public String getJsonrpc() {
            return jsonrpc;
        }

        public void setJsonrpc(String jsonrpc) {
            this.jsonrpc = jsonrpc;
        }

        public JsonNode getResult() {
            return result;
        }

        public void setResult(JsonNode result) {
            this.result = result;
        }

        public JsonRpcError getError() {
            return error;
        }

        public void setError(JsonRpcError error) {
            this.error = error;
        }

        public Object getId() {
            return id;
        }

        public void setId(Object id) {
            this.id = id;
        }

        public static JsonRpcResponse success(Object id, JsonNode result) {
            JsonRpcResponse response = new JsonRpcResponse();
            response.setId(id);
            response.setResult(result);
            return response;
        }

        public static JsonRpcResponse error(Object id, int code, String message) {
            return error(id, code, message, null);
        }

        public static JsonRpcResponse error(Object id, int code, String message, JsonNode data) {
            JsonRpcResponse response = new JsonRpcResponse();
            response.setId(id);
            JsonRpcError err = new JsonRpcError();
            err.setCode(code);
            err.setMessage(message);
            err.setData(data);
            response.setError(err);
            return response;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcError {
        private int code;
        private String message;
        private JsonNode data;

        public int getCode() {
            return code;
        }

        public void setCode(int code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public JsonNode getData() {
            return data;
        }

        public void setData(JsonNode data) {
            this.data = data;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class JsonRpcNotification {
        private String jsonrpc = "2.0";
        private String method;
        private JsonNode params;

        public String getJsonrpc() {
            return jsonrpc;
        }

        public void setJsonrpc(String jsonrpc) {
            this.jsonrpc = jsonrpc;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public JsonNode getParams() {
            return params;
        }

        public void setParams(JsonNode params) {
            this.params = params;
        }
    }
}
