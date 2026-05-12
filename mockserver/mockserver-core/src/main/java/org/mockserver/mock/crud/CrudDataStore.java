package org.mockserver.mock.crud;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.mockserver.model.CrudExpectationsDefinition.IdStrategy;
import org.mockserver.uuid.UUIDService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

public class CrudDataStore {

    public static final int DEFAULT_MAX_ITEMS = 10000;

    private final ConcurrentHashMap<String, ObjectNode> items = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> insertionOrder = new ConcurrentLinkedDeque<>();
    private final AtomicLong autoIncrementCounter = new AtomicLong(0);
    private final String idField;
    private final IdStrategy idStrategy;
    private final int maxItems;
    private final Object writeLock = new Object();

    public CrudDataStore(String idField, IdStrategy idStrategy) {
        this(idField, idStrategy, null, DEFAULT_MAX_ITEMS);
    }

    public CrudDataStore(String idField, IdStrategy idStrategy, List<ObjectNode> initialData) {
        this(idField, idStrategy, initialData, DEFAULT_MAX_ITEMS);
    }

    public CrudDataStore(String idField, IdStrategy idStrategy, List<ObjectNode> initialData, int maxItems) {
        this.idField = idField;
        this.idStrategy = idStrategy;
        this.maxItems = maxItems;
        if (initialData != null) {
            for (ObjectNode item : initialData) {
                ObjectNode copy = item.deepCopy();
                String id = extractId(copy);
                if (id != null) {
                    if (idStrategy == IdStrategy.AUTO_INCREMENT) {
                        try {
                            long numericId = Long.parseLong(id);
                            autoIncrementCounter.updateAndGet(current -> Math.max(current, numericId));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    items.put(id, copy);
                    insertionOrder.addLast(id);
                }
            }
        }
    }

    public List<ObjectNode> getAll() {
        List<ObjectNode> result = new ArrayList<>();
        for (String id : insertionOrder) {
            ObjectNode item = items.get(id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    public ObjectNode getById(String id) {
        return items.get(id);
    }

    public ObjectNode create(ObjectNode item) {
        synchronized (writeLock) {
            if (items.size() >= maxItems) {
                return null;
            }
            ObjectNode copy = item.deepCopy();
            String id = assignId(copy);
            items.put(id, copy);
            insertionOrder.addLast(id);
            return copy;
        }
    }

    public ObjectNode update(String id, ObjectNode item) {
        synchronized (writeLock) {
            if (!items.containsKey(id)) {
                return null;
            }
            ObjectNode copy = item.deepCopy();
            if (idStrategy == IdStrategy.AUTO_INCREMENT) {
                try {
                    copy.put(idField, Long.parseLong(id));
                } catch (NumberFormatException e) {
                    copy.put(idField, id);
                }
            } else {
                copy.put(idField, id);
            }
            items.put(id, copy);
            return copy;
        }
    }

    public boolean delete(String id) {
        synchronized (writeLock) {
            ObjectNode removed = items.remove(id);
            if (removed != null) {
                insertionOrder.remove(id);
                return true;
            }
            return false;
        }
    }

    public int size() {
        return items.size();
    }

    public int getMaxItems() {
        return maxItems;
    }

    public String getIdField() {
        return idField;
    }

    public IdStrategy getIdStrategy() {
        return idStrategy;
    }

    private String assignId(ObjectNode item) {
        if (idStrategy == IdStrategy.AUTO_INCREMENT) {
            long id = autoIncrementCounter.incrementAndGet();
            item.put(idField, id);
            return String.valueOf(id);
        } else {
            String id = UUIDService.getUUID();
            item.put(idField, id);
            return id;
        }
    }

    private String extractId(ObjectNode item) {
        if (item.has(idField)) {
            if (item.get(idField).isNumber()) {
                return String.valueOf(item.get(idField).asLong());
            }
            return item.get(idField).asText();
        }
        return null;
    }
}
