package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.JsonRpcBody;

import static junit.framework.TestCase.*;

public class JsonRpcBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        JsonRpcBody jsonRpcBody = new JsonRpcBody("tools/list");
        JsonRpcBodyDTO dto = new JsonRpcBodyDTO(jsonRpcBody);

        assertEquals("tools/list", dto.getMethod());
        assertNull(dto.getParamsSchema());
        assertEquals(Body.Type.JSON_RPC, dto.getType());
    }

    @Test
    public void shouldReturnValuesWithParamsSchema() {
        String schema = "{\"type\": \"object\", \"properties\": {\"name\": {\"type\": \"string\"}}}";
        JsonRpcBody jsonRpcBody = new JsonRpcBody("tools/call", schema);
        JsonRpcBodyDTO dto = new JsonRpcBodyDTO(jsonRpcBody);

        assertEquals("tools/call", dto.getMethod());
        assertEquals(schema, dto.getParamsSchema());
        assertEquals(Body.Type.JSON_RPC, dto.getType());
    }

    @Test
    public void shouldBuildObject() {
        JsonRpcBody original = new JsonRpcBody("tools/call", "{\"type\": \"object\"}");
        JsonRpcBodyDTO dto = new JsonRpcBodyDTO(original);
        JsonRpcBody rebuilt = dto.buildObject();

        assertEquals(original.getMethod(), rebuilt.getMethod());
        assertEquals(original.getParamsSchema(), rebuilt.getParamsSchema());
        assertEquals(Body.Type.JSON_RPC, rebuilt.getType());
    }

    @Test
    public void shouldPreserveNotFlag() {
        JsonRpcBody jsonRpcBody = new JsonRpcBody("tools/list");
        JsonRpcBodyDTO dto = new JsonRpcBodyDTO(jsonRpcBody, true);

        assertTrue(dto.getNot());
    }

    @Test
    public void shouldPreserveOptionalFlag() {
        JsonRpcBody jsonRpcBody = (JsonRpcBody) new JsonRpcBody("tools/list").withOptional(true);
        JsonRpcBodyDTO dto = new JsonRpcBodyDTO(jsonRpcBody);

        assertTrue(dto.getOptional());
        assertTrue(dto.buildObject().getOptional());
    }
}
