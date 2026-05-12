package org.mockserver.mock.crud;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.Test;
import org.mockserver.model.CrudExpectationsDefinition.IdStrategy;
import org.mockserver.serialization.ObjectMapperFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class CrudDataStoreTest {

    private final ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldCreateItemWithAutoIncrementId() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        ObjectNode item = objectMapper.createObjectNode().put("name", "Alice");

        // when
        ObjectNode created = store.create(item);

        // then
        assertThat(created.get("id").asLong(), is(1L));
        assertThat(created.get("name").asText(), is("Alice"));
        assertThat(store.size(), is(1));
    }

    @Test
    public void shouldCreateMultipleItemsWithAutoIncrementId() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);

        // when
        ObjectNode first = store.create(objectMapper.createObjectNode().put("name", "Alice"));
        ObjectNode second = store.create(objectMapper.createObjectNode().put("name", "Bob"));

        // then
        assertThat(first.get("id").asLong(), is(1L));
        assertThat(second.get("id").asLong(), is(2L));
        assertThat(store.size(), is(2));
    }

    @Test
    public void shouldCreateItemWithUuidId() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.UUID);
        ObjectNode item = objectMapper.createObjectNode().put("name", "Alice");

        // when
        ObjectNode created = store.create(item);

        // then
        assertThat(created.has("id"), is(true));
        assertThat(created.get("id").asText(), not(emptyOrNullString()));
        assertThat(created.get("name").asText(), is("Alice"));
    }

    @Test
    public void shouldGetAllItems() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));
        store.create(objectMapper.createObjectNode().put("name", "Bob"));

        // when
        List<ObjectNode> all = store.getAll();

        // then
        assertThat(all, hasSize(2));
        assertThat(all.get(0).get("name").asText(), is("Alice"));
        assertThat(all.get(1).get("name").asText(), is("Bob"));
    }

    @Test
    public void shouldGetAllItemsFromEmptyStore() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);

        // when
        List<ObjectNode> all = store.getAll();

        // then
        assertThat(all, hasSize(0));
    }

    @Test
    public void shouldGetItemById() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));

        // when
        ObjectNode item = store.getById("1");

        // then
        assertThat(item, is(notNullValue()));
        assertThat(item.get("name").asText(), is("Alice"));
    }

    @Test
    public void shouldReturnNullForNonExistentId() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);

        // when
        ObjectNode item = store.getById("999");

        // then
        assertThat(item, is(nullValue()));
    }

    @Test
    public void shouldUpdateItem() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));

        // when
        ObjectNode updated = store.update("1", objectMapper.createObjectNode().put("name", "Alice Updated"));

        // then
        assertThat(updated, is(notNullValue()));
        assertThat(updated.get("name").asText(), is("Alice Updated"));
        assertThat(updated.get("id").asLong(), is(1L));
        assertThat(store.getById("1").get("name").asText(), is("Alice Updated"));
    }

    @Test
    public void shouldReturnNullWhenUpdatingNonExistentItem() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);

        // when
        ObjectNode result = store.update("999", objectMapper.createObjectNode().put("name", "Ghost"));

        // then
        assertThat(result, is(nullValue()));
    }

    @Test
    public void shouldDeleteItem() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));

        // when
        boolean deleted = store.delete("1");

        // then
        assertThat(deleted, is(true));
        assertThat(store.size(), is(0));
        assertThat(store.getById("1"), is(nullValue()));
    }

    @Test
    public void shouldReturnFalseWhenDeletingNonExistentItem() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);

        // when
        boolean deleted = store.delete("999");

        // then
        assertThat(deleted, is(false));
    }

    @Test
    public void shouldInitializeWithInitialData() {
        // given
        ObjectNode alice = objectMapper.createObjectNode().put("id", 1).put("name", "Alice");
        ObjectNode bob = objectMapper.createObjectNode().put("id", 2).put("name", "Bob");

        // when
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, Arrays.asList(alice, bob));

        // then
        assertThat(store.size(), is(2));
        assertThat(store.getById("1").get("name").asText(), is("Alice"));
        assertThat(store.getById("2").get("name").asText(), is("Bob"));
    }

    @Test
    public void shouldContinueAutoIncrementAfterInitialData() {
        // given
        ObjectNode alice = objectMapper.createObjectNode().put("id", 5).put("name", "Alice");
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, Collections.singletonList(alice));

        // when
        ObjectNode created = store.create(objectMapper.createObjectNode().put("name", "Bob"));

        // then
        assertThat(created.get("id").asLong(), is(6L));
    }

    @Test
    public void shouldInitializeWithEmptyList() {
        // given / when
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, Collections.emptyList());

        // then
        assertThat(store.size(), is(0));
    }

    @Test
    public void shouldInitializeWithNullList() {
        // given / when
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, null);

        // then
        assertThat(store.size(), is(0));
    }

    @Test
    public void shouldUseCustomIdField() {
        // given
        CrudDataStore store = new CrudDataStore("userId", IdStrategy.AUTO_INCREMENT);

        // when
        ObjectNode created = store.create(objectMapper.createObjectNode().put("name", "Alice"));

        // then
        assertThat(created.has("userId"), is(true));
        assertThat(created.get("userId").asLong(), is(1L));
    }

    @Test
    public void shouldPreserveInsertionOrder() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Charlie"));
        store.create(objectMapper.createObjectNode().put("name", "Alice"));
        store.create(objectMapper.createObjectNode().put("name", "Bob"));

        // when
        List<ObjectNode> all = store.getAll();

        // then
        assertThat(all.get(0).get("name").asText(), is("Charlie"));
        assertThat(all.get(1).get("name").asText(), is("Alice"));
        assertThat(all.get(2).get("name").asText(), is("Bob"));
    }

    @Test
    public void shouldNotModifyOriginalItemOnCreate() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        ObjectNode original = objectMapper.createObjectNode().put("name", "Alice");

        // when
        store.create(original);

        // then
        assertThat(original.has("id"), is(false));
    }

    @Test
    public void shouldNotModifyOriginalItemOnUpdate() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));
        ObjectNode updateData = objectMapper.createObjectNode().put("name", "Alice Updated");

        // when
        store.update("1", updateData);

        // then
        assertThat(updateData.has("id"), is(false));
    }

    @Test
    public void shouldUpdateItemWithUuidIdStrategy() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.UUID);
        ObjectNode created = store.create(objectMapper.createObjectNode().put("name", "Alice"));
        String id = created.get("id").asText();

        // when
        ObjectNode updated = store.update(id, objectMapper.createObjectNode().put("name", "Alice Updated"));

        // then
        assertThat(updated, is(notNullValue()));
        assertThat(updated.get("id").asText(), is(id));
        assertThat(updated.get("name").asText(), is("Alice Updated"));
    }

    @Test
    public void shouldHandleConcurrentCreates() throws Exception {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    store.create(objectMapper.createObjectNode().put("name", "User" + index));
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // then
        assertThat(store.size(), is(threadCount));
        assertThat(store.getAll(), hasSize(threadCount));
    }

    @Test
    public void shouldRejectCreateWhenMaxItemsReached() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, null, 2);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));
        store.create(objectMapper.createObjectNode().put("name", "Bob"));

        ObjectNode result = store.create(objectMapper.createObjectNode().put("name", "Charlie"));

        assertThat(result, is(nullValue()));
        assertThat(store.size(), is(2));
    }

    @Test
    public void shouldAllowCreateAfterDeleteWhenAtMaxItems() {
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT, null, 2);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));
        store.create(objectMapper.createObjectNode().put("name", "Bob"));
        store.delete("1");

        ObjectNode result = store.create(objectMapper.createObjectNode().put("name", "Charlie"));

        assertThat(result, is(notNullValue()));
        assertThat(store.size(), is(2));
    }

    @Test
    public void shouldHandleDeletedItemsInGetAll() {
        // given
        CrudDataStore store = new CrudDataStore("id", IdStrategy.AUTO_INCREMENT);
        store.create(objectMapper.createObjectNode().put("name", "Alice"));
        store.create(objectMapper.createObjectNode().put("name", "Bob"));
        store.create(objectMapper.createObjectNode().put("name", "Charlie"));

        // when
        store.delete("2");
        List<ObjectNode> all = store.getAll();

        // then
        assertThat(all, hasSize(2));
        assertThat(all.get(0).get("name").asText(), is("Alice"));
        assertThat(all.get(1).get("name").asText(), is("Charlie"));
    }
}
