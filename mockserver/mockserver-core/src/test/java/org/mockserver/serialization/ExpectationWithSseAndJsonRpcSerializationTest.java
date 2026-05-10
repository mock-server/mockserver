package org.mockserver.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.model.ExpectationDTO;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpSseResponse.sseResponse;
import static org.mockserver.model.JsonRpcBody.jsonRpc;
import static org.mockserver.model.SseEvent.sseEvent;

public class ExpectationWithSseAndJsonRpcSerializationTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldSerializeExpectationWithSseResponse() throws Exception {
        Expectation expectation = Expectation.when(
            request().withMethod("POST").withPath("/events")
        ).thenRespondWithSse(
            sseResponse()
                .withStatusCode(200)
                .withEvent(sseEvent().withEvent("message").withData("{\"hello\": \"world\"}").withId("1"))
                .withEvent(sseEvent().withEvent("update").withData("data2").withDelay(TimeUnit.MILLISECONDS, 100))
                .withCloseConnection(true)
        );

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new ExpectationDTO(expectation));
        assertNotNull(json);
        assertTrue(json.contains("httpSseResponse"));
        assertTrue(json.contains("message"));

        ExpectationDTO rebuiltDTO = objectMapper.readValue(json, ExpectationDTO.class);
        Expectation rebuilt = rebuiltDTO.buildObject();

        assertNotNull(rebuilt.getHttpSseResponse());
        assertEquals(Integer.valueOf(200), rebuilt.getHttpSseResponse().getStatusCode());
        assertEquals(2, rebuilt.getHttpSseResponse().getEvents().size());
        assertEquals("message", rebuilt.getHttpSseResponse().getEvents().get(0).getEvent());
        assertEquals("{\"hello\": \"world\"}", rebuilt.getHttpSseResponse().getEvents().get(0).getData());
        assertEquals("1", rebuilt.getHttpSseResponse().getEvents().get(0).getId());
        assertEquals("update", rebuilt.getHttpSseResponse().getEvents().get(1).getEvent());
        assertNotNull(rebuilt.getHttpSseResponse().getEvents().get(1).getDelay());
        assertTrue(rebuilt.getHttpSseResponse().getCloseConnection());
    }

    @Test
    public void shouldSerializeExpectationWithJsonRpcBody() throws Exception {
        Expectation expectation = Expectation.when(
            request()
                .withMethod("POST")
                .withPath("/rpc")
                .withBody(jsonRpc("tools/call"))
        ).thenRespond(
            HttpResponse.response()
                .withStatusCode(200)
                .withBody("{\"jsonrpc\": \"2.0\", \"result\": {}, \"id\": 1}")
        );

        String json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new ExpectationDTO(expectation));
        assertNotNull(json);
        assertTrue(json.contains("JSON_RPC"));

        ExpectationDTO rebuiltDTO = objectMapper.readValue(json, ExpectationDTO.class);
        Expectation rebuilt = rebuiltDTO.buildObject();

        assertNotNull(rebuilt.getHttpRequest());
        HttpRequest rebuiltRequest = (HttpRequest) rebuilt.getHttpRequest();
        assertNotNull(rebuiltRequest.getBody());
        assertEquals(Body.Type.JSON_RPC, rebuiltRequest.getBody().getType());
        assertTrue(rebuiltRequest.getBody() instanceof JsonRpcBody);
        assertEquals("tools/call", ((JsonRpcBody) rebuiltRequest.getBody()).getMethod());
    }

    @Test
    public void shouldSerializeExpectationWithJsonRpcBodyAndParamsSchema() throws Exception {
        String paramsSchema = "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}, \"required\": [\"name\"]}";
        Expectation expectation = Expectation.when(
            request()
                .withMethod("POST")
                .withPath("/rpc")
                .withBody(jsonRpc("tools/call", paramsSchema))
        ).thenRespond(
            HttpResponse.response().withStatusCode(200)
        );

        String json = objectMapper.writeValueAsString(new ExpectationDTO(expectation));
        assertNotNull(json);

        ExpectationDTO rebuiltDTO = objectMapper.readValue(json, ExpectationDTO.class);
        Expectation rebuilt = rebuiltDTO.buildObject();

        HttpRequest rebuiltRequest = (HttpRequest) rebuilt.getHttpRequest();
        JsonRpcBody rebuiltBody = (JsonRpcBody) rebuiltRequest.getBody();
        assertEquals("tools/call", rebuiltBody.getMethod());
        assertNotNull(rebuiltBody.getParamsSchema());
    }

    @Test
    public void shouldAllowBothSseResponseAndRegularResponse() {
        Expectation expectation = Expectation.when(request())
            .thenRespond(HttpResponse.response().withStatusCode(200))
            .thenRespondWithSse(sseResponse().withEvent(sseEvent().withData("test")));
        assertNotNull(expectation.getHttpResponse());
        assertNotNull(expectation.getHttpSseResponse());
    }
}
