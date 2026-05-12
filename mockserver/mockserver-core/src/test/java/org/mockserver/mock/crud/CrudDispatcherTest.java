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

public class CrudDispatcherTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();
    private CrudDispatcher dispatcher;

    @Before
    public void setUp() {
        dispatcher = new CrudDispatcher();
        ObjectNode alice = objectMapper.createObjectNode().put("id", 1).put("name", "Alice");
        ObjectNode bob = objectMapper.createObjectNode().put("id", 2).put("name", "Bob");
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, Arrays.asList(alice, bob));
        CrudActionHandler handler = new CrudActionHandler(store, "/api/users");
        dispatcher.register("/api/users", handler);
    }

    // Registration

    @Test
    public void shouldRegisterHandler() {
        assertThat(dispatcher.hasHandler("/api/users"), is(true));
        assertThat(dispatcher.size(), is(1));
    }

    @Test
    public void shouldUnregisterHandler() {
        // when
        dispatcher.unregister("/api/users");

        // then
        assertThat(dispatcher.hasHandler("/api/users"), is(false));
        assertThat(dispatcher.size(), is(0));
    }

    @Test
    public void shouldResetAllHandlers() {
        // given
        CrudDataStore store2 = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("/api/products", new CrudActionHandler(store2, "/api/products"));
        assertThat(dispatcher.size(), is(2));

        // when
        dispatcher.reset();

        // then
        assertThat(dispatcher.size(), is(0));
    }

    // GET list dispatch

    @Test
    public void shouldDispatchGetList() throws Exception {
        // given
        HttpRequest request = request("/api/users").withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(200));
        ArrayNode body = (ArrayNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.size(), is(2));
    }

    // GET by ID dispatch

    @Test
    public void shouldDispatchGetById() throws Exception {
        // given
        HttpRequest request = request("/api/users/1").withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(200));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("name").asText(), is("Alice"));
    }

    // POST create dispatch

    @Test
    public void shouldDispatchCreate() throws Exception {
        // given
        HttpRequest request = request("/api/users")
            .withMethod("POST")
            .withBody("{\"name\":\"Charlie\"}");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(201));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("name").asText(), is("Charlie"));
    }

    // PUT update dispatch

    @Test
    public void shouldDispatchUpdate() throws Exception {
        // given
        HttpRequest request = request("/api/users/1")
            .withMethod("PUT")
            .withBody("{\"name\":\"Alice Updated\"}");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(200));
        ObjectNode body = (ObjectNode) objectMapper.readTree(response.getBodyAsString());
        assertThat(body.get("name").asText(), is("Alice Updated"));
    }

    // DELETE dispatch

    @Test
    public void shouldDispatchDelete() {
        // given
        HttpRequest request = request("/api/users/1").withMethod("DELETE");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(204));
    }

    // Non-matching paths

    @Test
    public void shouldReturnNullForNonMatchingPath() {
        // given
        HttpRequest request = request("/api/products").withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(nullValue()));
    }

    @Test
    public void shouldReturnNullForNullPath() {
        // given
        HttpRequest request = request().withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(nullValue()));
    }

    @Test
    public void shouldReturnNullForUnsupportedMethodOnBasePath() {
        // given
        HttpRequest request = request("/api/users").withMethod("DELETE");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(nullValue()));
    }

    @Test
    public void shouldReturnNullForUnsupportedMethodOnIdPath() {
        // given
        HttpRequest request = request("/api/users/1").withMethod("POST");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(nullValue()));
    }

    // Multiple resources

    @Test
    public void shouldDispatchToCorrectResource() throws Exception {
        // given
        ObjectNode product = objectMapper.createObjectNode().put("id", 1).put("name", "Widget");
        CrudDataStore productStore = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, java.util.Collections.singletonList(product));
        dispatcher.register("/api/products", new CrudActionHandler(productStore, "/api/products"));

        HttpRequest usersRequest = request("/api/users").withMethod("GET");
        HttpRequest productsRequest = request("/api/products").withMethod("GET");

        // when
        HttpResponse usersResponse = dispatcher.dispatch(usersRequest);
        HttpResponse productsResponse = dispatcher.dispatch(productsRequest);

        // then
        assertThat(usersResponse, is(notNullValue()));
        ArrayNode usersBody = (ArrayNode) objectMapper.readTree(usersResponse.getBodyAsString());
        assertThat(usersBody.get(0).get("name").asText(), is("Alice"));

        assertThat(productsResponse, is(notNullValue()));
        ArrayNode productsBody = (ArrayNode) objectMapper.readTree(productsResponse.getBodyAsString());
        assertThat(productsBody.get(0).get("name").asText(), is("Widget"));
    }

    // Trailing slash handling

    @Test
    public void shouldMatchPathWithTrailingSlash() throws Exception {
        // given
        HttpRequest request = request("/api/users/").withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(notNullValue()));
        assertThat(response.getStatusCode(), is(200));
    }

    // Nested paths should not match

    @Test
    public void shouldNotMatchNestedPaths() {
        // given
        HttpRequest request = request("/api/users/1/orders").withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(nullValue()));
    }

    // Path prefix that partially matches another resource

    @Test
    public void shouldNotMatchPartialPathPrefix() {
        // given
        HttpRequest request = request("/api/usersettings").withMethod("GET");

        // when
        HttpResponse response = dispatcher.dispatch(request);

        // then
        assertThat(response, is(nullValue()));
    }

    // Full CRUD lifecycle through dispatcher

    @Test
    public void shouldSupportFullCrudLifecycle() throws Exception {
        // given
        CrudDataStore emptyStore = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        CrudDispatcher lifecycle = new CrudDispatcher();
        lifecycle.register("/api/items", new CrudActionHandler(emptyStore, "/api/items"));

        // create
        HttpRequest createRequest = request("/api/items")
            .withMethod("POST")
            .withBody("{\"name\":\"Widget\"}");
        HttpResponse createResponse = lifecycle.dispatch(createRequest);
        assertThat(createResponse.getStatusCode(), is(201));
        ObjectNode created = (ObjectNode) objectMapper.readTree(createResponse.getBodyAsString());
        String id = String.valueOf(created.get("id").asLong());

        // list
        HttpResponse listResponse = lifecycle.dispatch(request("/api/items").withMethod("GET"));
        assertThat(listResponse.getStatusCode(), is(200));
        ArrayNode list = (ArrayNode) objectMapper.readTree(listResponse.getBodyAsString());
        assertThat(list.size(), is(1));

        // get by ID
        HttpResponse getResponse = lifecycle.dispatch(request("/api/items/" + id).withMethod("GET"));
        assertThat(getResponse.getStatusCode(), is(200));

        // update
        HttpRequest updateRequest = request("/api/items/" + id)
            .withMethod("PUT")
            .withBody("{\"name\":\"Updated Widget\"}");
        HttpResponse updateResponse = lifecycle.dispatch(updateRequest);
        assertThat(updateResponse.getStatusCode(), is(200));
        ObjectNode updated = (ObjectNode) objectMapper.readTree(updateResponse.getBodyAsString());
        assertThat(updated.get("name").asText(), is("Updated Widget"));

        // delete
        HttpResponse deleteResponse = lifecycle.dispatch(request("/api/items/" + id).withMethod("DELETE"));
        assertThat(deleteResponse.getStatusCode(), is(204));

        // verify deleted
        HttpResponse afterDelete = lifecycle.dispatch(request("/api/items/" + id).withMethod("GET"));
        assertThat(afterDelete.getStatusCode(), is(404));

        // list should be empty
        HttpResponse emptyList = lifecycle.dispatch(request("/api/items").withMethod("GET"));
        assertThat(emptyList.getStatusCode(), is(200));
        ArrayNode emptyArray = (ArrayNode) objectMapper.readTree(emptyList.getBodyAsString());
        assertThat(emptyArray.size(), is(0));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectEmptyBasePath() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("", new CrudActionHandler(store, ""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBasePathWithoutLeadingSlash() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("api/users", new CrudActionHandler(store, "api/users"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBasePathOverlappingMockserverControlPlane() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("/mockserver/something", new CrudActionHandler(store, "/mockserver/something"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBasePathAtExactMockserverPath() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("/mockserver", new CrudActionHandler(store, "/mockserver"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectBasePathWithPathTraversal() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("/api/../admin", new CrudActionHandler(store, "/api/../admin"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void shouldRejectRootBasePath() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        dispatcher.register("/", new CrudActionHandler(store, "/"));
    }
}
