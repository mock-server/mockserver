package org.mockserver.netty.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

public class JsonRpcMessageTest {

    private ObjectMapper objectMapper;

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldSerializeJsonRpcRequest() throws Exception {
        JsonRpcMessage.JsonRpcRequest request = new JsonRpcMessage.JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("test/method");
        request.setId(1);

        String json = objectMapper.writeValueAsString(request);
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.path("jsonrpc").asText(), is("2.0"));
        assertThat(node.path("method").asText(), is("test/method"));
        assertThat(node.path("id").asInt(), is(1));
    }

    @Test
    public void shouldDeserializeJsonRpcRequest() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"tools/list\",\"id\":42,\"params\":{\"key\":\"value\"}}";
        JsonRpcMessage.JsonRpcRequest request = objectMapper.readValue(json, JsonRpcMessage.JsonRpcRequest.class);

        assertThat(request.getJsonrpc(), is("2.0"));
        assertThat(request.getMethod(), is("tools/list"));
        assertThat(request.getParams(), notNullValue());
        assertThat(request.getParams().path("key").asText(), is("value"));
    }

    @Test
    public void shouldDetectNotificationWhenIdNotPresent() throws Exception {
        JsonRpcMessage.JsonRpcRequest notification = new JsonRpcMessage.JsonRpcRequest();
        notification.setJsonrpc("2.0");
        notification.setMethod("notifications/initialized");
        notification.setIdPresent(false);

        assertThat(notification.isNotification(), is(true));
        assertThat(notification.isIdPresent(), is(false));
    }

    @Test
    public void shouldNotBeNotificationWhenIdPresent() throws Exception {
        JsonRpcMessage.JsonRpcRequest request = new JsonRpcMessage.JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("ping");
        request.setId(1);
        request.setIdPresent(true);

        assertThat(request.isNotification(), is(false));
        assertThat(request.isIdPresent(), is(true));
    }

    @Test
    public void shouldNotBeNotificationWhenIdIsNullButPresent() throws Exception {
        JsonRpcMessage.JsonRpcRequest request = new JsonRpcMessage.JsonRpcRequest();
        request.setJsonrpc("2.0");
        request.setMethod("ping");
        request.setId(null);
        request.setIdPresent(true);

        assertThat(request.isNotification(), is(false));
        assertThat(request.getId(), nullValue());
    }

    @Test
    public void shouldCreateSuccessResponse() {
        ObjectNode result = objectMapper.createObjectNode();
        result.put("data", "test");
        JsonRpcMessage.JsonRpcResponse response = JsonRpcMessage.JsonRpcResponse.success(1, result);

        assertThat(response.getJsonrpc(), is("2.0"));
        assertThat(response.getId(), is(1));
        assertThat(response.getResult().path("data").asText(), is("test"));
        assertThat(response.getError(), nullValue());
    }

    @Test
    public void shouldCreateErrorResponse() {
        JsonRpcMessage.JsonRpcResponse response = JsonRpcMessage.JsonRpcResponse.error(
            2, JsonRpcMessage.METHOD_NOT_FOUND, "Method not found"
        );

        assertThat(response.getJsonrpc(), is("2.0"));
        assertThat(response.getId(), is(2));
        assertThat(response.getResult(), nullValue());
        assertThat(response.getError(), notNullValue());
        assertThat(response.getError().getCode(), is(-32601));
        assertThat(response.getError().getMessage(), is("Method not found"));
    }

    @Test
    public void shouldCreateErrorResponseWithData() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("detail", "extra info");
        JsonRpcMessage.JsonRpcResponse response = JsonRpcMessage.JsonRpcResponse.error(
            3, JsonRpcMessage.INTERNAL_ERROR, "Internal error", data
        );

        assertThat(response.getError().getData(), notNullValue());
        assertThat(response.getError().getData().path("detail").asText(), is("extra info"));
    }

    @Test
    public void shouldSerializeResponse() throws Exception {
        JsonRpcMessage.JsonRpcResponse response = JsonRpcMessage.JsonRpcResponse.success(
            1, objectMapper.createObjectNode()
        );
        String json = objectMapper.writeValueAsString(response);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.path("jsonrpc").asText(), is("2.0"));
        assertThat(node.path("id").asInt(), is(1));
        assertThat(node.has("result"), is(true));
        assertThat(node.has("error"), is(false));
    }

    @Test
    public void shouldHandleStringId() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\",\"method\":\"ping\",\"id\":\"abc-123\"}";
        JsonRpcMessage.JsonRpcRequest request = objectMapper.readValue(json, JsonRpcMessage.JsonRpcRequest.class);
        assertThat(request.getId().toString(), is("abc-123"));
    }

    @Test
    public void shouldHaveCorrectErrorCodes() {
        assertThat(JsonRpcMessage.PARSE_ERROR, is(-32700));
        assertThat(JsonRpcMessage.INVALID_REQUEST, is(-32600));
        assertThat(JsonRpcMessage.METHOD_NOT_FOUND, is(-32601));
        assertThat(JsonRpcMessage.INVALID_PARAMS, is(-32602));
        assertThat(JsonRpcMessage.INTERNAL_ERROR, is(-32603));
    }

    @Test
    public void shouldHandleNotification() {
        JsonRpcMessage.JsonRpcNotification notification = new JsonRpcMessage.JsonRpcNotification();
        notification.setJsonrpc("2.0");
        notification.setMethod("notifications/initialized");

        assertThat(notification.getJsonrpc(), is("2.0"));
        assertThat(notification.getMethod(), is("notifications/initialized"));
        assertThat(notification.getParams(), nullValue());
    }
}
