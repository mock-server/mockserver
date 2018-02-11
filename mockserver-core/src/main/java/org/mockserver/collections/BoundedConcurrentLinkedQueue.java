package org.mockserver.collections;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * @author jamesdbloom
 */
public class BoundedConcurrentLinkedQueue<E> extends ConcurrentLinkedQueue<E> {
    static final long serialVersionUID = -8190199206751953870L;
    private final int maxSize;

    public BoundedConcurrentLinkedQueue(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(E element) {
        if (size() >= maxSize) {
            super.poll();
        }
        return super.add(element);
    }

    @Override
    public boolean addAll(Collection<? extends E> collection) {
        boolean result = false;
        for (E element : collection) {
            if (add(element)) {
                result = true;
            }
        }
        return result;
    }

    @Override
    public boolean offer(E element) {
        if (size() >= maxSize) {
            super.poll();
        }
        return super.offer(element);
    }

}
