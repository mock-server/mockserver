package org.mockserver.collections;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

/**
 * @author jamesdbloom
 */
class CircularLinkedList<V> extends LinkedList<V> {
    private final int maxSize;

    public CircularLinkedList(int maxSize) {
        this.maxSize = maxSize;
    }

    @Override
    public void addFirst(V v) {
        if (size() > (maxSize - 1)) {
            removeFirst();
        }
        super.addFirst(v);
    }

    @Override
    public void addLast(V v) {
        super.addLast(v);
        if (size() > maxSize) {
            removeFirst();
        }
    }

    @Override
    public boolean add(V v) {
        boolean changed = super.add(v);
        if (size() > maxSize) {
            removeFirst();
        }
        return changed;
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        boolean changed = false;
        for (V value : c) {
            if (add(value)) {
                changed = true;
            }
        }
        return changed;
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> c) {
        List<V> objects = new ArrayList<>(c);
        for (int i = 0; i < c.size(); i++) {
            add(index, objects.get(i));
        }
        return true;
    }

    @Override
    public void add(int index, V element) {
        if (index < size()) {
            super.add(index, element);
            if (size() > maxSize) {
                removeFirst();
            }
        } else {
            throw new IllegalArgumentException("Index [" + index + "] is greater then the max size [" + maxSize + "]");
        }
    }
}
