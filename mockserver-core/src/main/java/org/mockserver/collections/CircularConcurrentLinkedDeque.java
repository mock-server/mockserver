package org.mockserver.collections;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.function.Consumer;

/**
 * @author jamesdbloom
 */
public class CircularConcurrentLinkedDeque<E> extends ConcurrentLinkedDeque<E> {
    private int maxSize;
    private final Consumer<E> onEvictCallback;

    public CircularConcurrentLinkedDeque(int maxSize, Consumer<E> onEvictCallback) {
        this.maxSize = maxSize;
        this.onEvictCallback = onEvictCallback;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(E element) {
        if (maxSize > 0) {
            evictExcessElements();
            return super.add(element);
        } else {
            return false;
        }
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        if (maxSize > 0) {
            boolean result = false;
            for (E element : collection) {
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
    public boolean offer(E element) {
        if (maxSize > 0) {
            evictExcessElements();
            return super.offer(element);
        } else {
            return false;
        }
    }

    private void evictExcessElements() {
        if (onEvictCallback == null) {
            while (size() >= maxSize) {
                super.poll();
            }
        } else {
            while (size() >= maxSize) {
                onEvictCallback.accept(super.poll());
            }
        }
    }

    public void clear() {
        if (onEvictCallback == null) {
            super.clear();
        } else {
            while (size() > 0) {
                onEvictCallback.accept(super.poll());
            }
        }
    }

    /**
     * @deprecated use removeItem instead
     */
    @Deprecated
    public boolean remove(Object o) {
        return super.remove(o);
    }

    public boolean removeItem(E e) {
        if (onEvictCallback != null) {
            onEvictCallback.accept(e);
        }
        return super.remove(e);
    }
}
