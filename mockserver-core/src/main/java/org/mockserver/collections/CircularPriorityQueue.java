package org.mockserver.collections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.function.Function;

/**
 * @author jamesdbloom
 */
public class CircularPriorityQueue<K, V> extends PriorityBlockingQueue<V> {
    private int maxSize;
    private final Class<V> valueType;
    private final Function<V, K> keyFunction;
    private ConcurrentLinkedQueue<V> insertionOrderQueue = new ConcurrentLinkedQueue<>();
    private ConcurrentMap<K, V> byKey = new ConcurrentHashMap<>();

    public CircularPriorityQueue(int maxSize, Class<V> valueType, Comparator<? super V> comparator, Function<V, K> keyFunction) {
        super(50, comparator);
        this.maxSize = maxSize;
        this.valueType = valueType;
        this.keyFunction = keyFunction;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean addAll(Collection<? extends V> collection) {
        if (maxSize > 0) {
            boolean result = false;
            for (V element : collection) {
                if (add(element)) {
                    result = true;
                }
            }
            return result;
        } else {
            return false;
        }
    }

    @Override
    public boolean offer(V element) {
        if (maxSize > 0) {
            boolean updated = super.offer(element);
            insertionOrderQueue.offer(element);
            byKey.put(keyFunction.apply(element), element);
            while (insertionOrderQueue.size() > maxSize) {
                V elementToRemove = insertionOrderQueue.poll();
                super.remove(elementToRemove);
                byKey.remove(keyFunction.apply(elementToRemove));
            }
            return updated;
        } else {
            return false;
        }
    }

    @Override
    public boolean remove(Object elementToRemove) {
        if (valueType.isAssignableFrom(elementToRemove.getClass())) {
            byKey.remove(keyFunction.apply(valueType.cast(elementToRemove)));
            insertionOrderQueue.remove(elementToRemove);
            return super.remove(elementToRemove);
        }
        return false;
    }

    public List<V> toSortedList() {
        PriorityBlockingQueue<V> secondQueue = new PriorityBlockingQueue<>(this);
        List<V> contents = new ArrayList<>(secondQueue.size());
        while (secondQueue.size() > 0) {
            contents.add(secondQueue.poll());
        }
        return contents;
    }

    public Optional<V> getByKey(K key) {
        return Optional.ofNullable(byKey.get(key));
    }

    public Map<K, V> keyMap() {
        return new HashMap<>(byKey);
    }
}
