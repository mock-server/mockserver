package org.mockserver.collections;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * @author jamesdbloom
 */
public class BoundedConcurrentLinkedDeque<E> extends ConcurrentLinkedDeque<E> {
    private final int maxSize;

    public BoundedConcurrentLinkedDeque(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public boolean add(E element) {
        if (maxSize > 0) {
            if (size() >= maxSize) {
                super.poll();
            }
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
            if (size() >= maxSize) {
                super.poll();
            }
            return super.offer(element);
        } else {
            return false;
        }
    }

}
