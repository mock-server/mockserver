package org.mockserver.mock.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.CrudExpectationsDefinition.IdStrategy;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockserver.model.HttpRequest.request;

public class CrudActionHandlerTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private CrudDataStore store;
    private CrudActionHandler handler;

    @Before
    public void setUp() {
        ObjectNode alice = objectMapper.createObjectNode().put("id", 1).put("name", "Alice").put("email", "alice@example.com");
        ObjectNode bob = objectMapper.createObjectNode().put("id", 2).put("name", "Bob").put("email", "bob@example.com");
        store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, Arrays.asList(alice, bob));
        handler = new CrudActionHandler(store, "/api/users");
    }

    // GET list

    @Test
    public void shouldListAllItems() throws Exception {
        // given
        HttpRequest request = request("/api/users").withMethod("GET");

        // when
        HttpResponse response = handler.handleList(request);

        // then
        assertThat(response.getStatusCode(), is(200));
        ArrayNode body = (ArrayNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.size(), is(2));
        assertThat(body.get(0).get("name").asText(), is("Alice"));
        assertThat(body.get(1).get("name").asText(), is("Bob"));
    }

    @Test
    public void shouldListEmptyStore() throws Exception {
        // given
        CrudDataStore emptyStore = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        CrudActionHandler emptyHandler = new CrudActionHandler(emptyStore, "/api/users");
        HttpRequest request = request("/api/users").withMethod("GET");

        // when
        HttpResponse response = emptyHandler.handleList(request);

        // then
        assertThat(response.getStatusCode(), is(200));
        ArrayNode body = (ArrayNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.size(), is(0));
    }

    // GET by ID

    @Test
    public void shouldGetItemById() throws Exception {
        // given
        HttpRequest request = request("/api/users/1").withMethod("GET");

        // when
        HttpResponse response = handler.handleGetById(request);

        // then
        assertThat(response.getStatusCode(), is(200));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("name").asText(), is("Alice"));
        assertThat(body.get("email").asText(), is("alice@example.com"));
    }

    @Test
    public void shouldReturn404ForNonExistentItem() {
        // given
        HttpRequest request = request("/api/users/999").withMethod("GET");

        // when
        HttpResponse response = handler.handleGetById(request);

        // then
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getBodyAsString(), containsString("not found"));
    }

    @Test
    public void shouldReturn400ForMissingIdInGetById() {
        // given
        HttpRequest request = request("/api/users").withMethod("GET");

        // when
        HttpResponse response = handler.handleGetById(request);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("missing id"));
    }

    // POST create

    @Test
    public void shouldCreateItem() throws Exception {
        // given
        HttpRequest request = request("/api/users")
            .withMethod("POST")
            .withBody("{\"name\":\"Charlie\",\"email\":\"charlie@example.com\"}");

        // when
        HttpResponse response = handler.handleCreate(request);

        // then
        assertThat(response.getStatusCode(), is(201));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("name").asText(), is("Charlie"));
        assertThat(body.get("email").asText(), is("charlie@example.com"));
        assertThat(body.has("id"), is(true));
        assertThat(body.get("id").asLong(), is(3L));
        assertThat(store.size(), is(3));
    }

    @Test
    public void shouldReturn400ForEmptyBodyOnCreate() {
        // given
        HttpRequest request = request("/api/users").withMethod("POST");

        // when
        HttpResponse response = handler.handleCreate(request);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("request body is required"));
    }

    @Test
    public void shouldReturn400ForInvalidJsonOnCreate() {
        // given
        HttpRequest request = request("/api/users")
            .withMethod("POST")
            .withBody("not json");

        // when
        HttpResponse response = handler.handleCreate(request);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("invalid request body"));
    }

    // PUT update

    @Test
    public void shouldUpdateItem() throws Exception {
        // given
        HttpRequest request = request("/api/users/1")
            .withMethod("PUT")
            .withBody("{\"name\":\"Alice Updated\",\"email\":\"alice.updated@example.com\"}");

        // when
        HttpResponse response = handler.handleUpdate(request);

        // then
        assertThat(response.getStatusCode(), is(200));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("name").asText(), is("Alice Updated"));
        assertThat(body.get("email").asText(), is("alice.updated@example.com"));
        assertThat(body.get("id").asLong(), is(1L));
    }

    @Test
    public void shouldReturn404WhenUpdatingNonExistentItem() {
        // given
        HttpRequest request = request("/api/users/999")
            .withMethod("PUT")
            .withBody("{\"name\":\"Ghost\"}");

        // when
        HttpResponse response = handler.handleUpdate(request);

        // then
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getBodyAsString(), containsString("not found"));
    }

    @Test
    public void shouldReturn400ForEmptyBodyOnUpdate() {
        // given
        HttpRequest request = request("/api/users/1").withMethod("PUT");

        // when
        HttpResponse response = handler.handleUpdate(request);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("request body is required"));
    }

    @Test
    public void shouldReturn400ForMissingIdInUpdate() {
        // given
        HttpRequest request = request("/api/users")
            .withMethod("PUT")
            .withBody("{\"name\":\"Alice\"}");

        // when
        HttpResponse response = handler.handleUpdate(request);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("missing id"));
    }

    // DELETE

    @Test
    public void shouldDeleteItem() {
        // given
        HttpRequest request = request("/api/users/1").withMethod("DELETE");

        // when
        HttpResponse response = handler.handleDelete(request);

        // then
        assertThat(response.getStatusCode(), is(204));
        assertThat(store.size(), is(1));
        assertThat(store.getById("1"), is(nullValue()));
    }

    @Test
    public void shouldReturn404WhenDeletingNonExistentItem() {
        // given
        HttpRequest request = request("/api/users/999").withMethod("DELETE");

        // when
        HttpResponse response = handler.handleDelete(request);

        // then
        assertThat(response.getStatusCode(), is(404));
        assertThat(response.getBodyAsString(), containsString("not found"));
    }

    @Test
    public void shouldReturn400ForMissingIdInDelete() {
        // given
        HttpRequest request = request("/api/users").withMethod("DELETE");

        // when
        HttpResponse response = handler.handleDelete(request);

        // then
        assertThat(response.getStatusCode(), is(400));
        assertThat(response.getBodyAsString(), containsString("missing id"));
    }

    // Path extraction

    @Test
    public void shouldExtractIdFromPath() {
        // given
        HttpRequest request = request("/api/users/42").withMethod("GET");

        // when
        String id = handler.extractIdFromPath(request);

        // then
        assertThat(id, is("42"));
    }

    @Test
    public void shouldExtractStringIdFromPath() {
        // given
        HttpRequest request = request("/api/users/abc-123").withMethod("GET");

        // when
        String id = handler.extractIdFromPath(request);

        // then
        assertThat(id, is("abc-123"));
    }

    @Test
    public void shouldReturnNullForBasePathOnly() {
        // given
        HttpRequest request = request("/api/users").withMethod("GET");

        // when
        String id = handler.extractIdFromPath(request);

        // then
        assertThat(id, is(nullValue()));
    }

    @Test
    public void shouldHandleTrailingSlashOnBasePath() {
        // given
        CrudActionHandler handlerWithSlash = new CrudActionHandler(store, "/api/users/");
        HttpRequest request = request("/api/users/1").withMethod("GET");

        // when
        String id = handlerWithSlash.extractIdFromPath(request);

        // then
        assertThat(id, is("1"));
    }

    // UUID strategy

    @Test
    public void shouldCreateItemWithUuidStrategy() throws Exception {
        // given
        CrudDataStore uuidStore = new CrudDataStore("id", IdStrategy.UUID);
        CrudActionHandler uuidHandler = new CrudActionHandler(uuidStore, "/api/items");
        HttpRequest request = request("/api/items")
            .withMethod("POST")
            .withBody("{\"name\":\"Item1\"}");

        // when
        HttpResponse response = uuidHandler.handleCreate(request);

        // then
        assertThat(response.getStatusCode(), is(201));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.has("id"), is(true));
        assertThat(body.get("id").asText(), not(emptyOrNullString()));
    }
}
