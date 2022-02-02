package org.mockserver.model;

import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class KeysToMultiValuesModifier<T extends KeysToMultiValues<I, T>, K extends KeysToMultiValuesModifier<T, K, I>, I extends KeyToMultiValue> {

    private int hashCode;
    private T add;
    private T replace;
    private List<String> remove;

    public T getAdd() {
        return add;
    }

    public K withAdd(T add) {
        this.add = add;
        this.hashCode = 0;
        return (K) this;
    }

    public T getReplace() {
        return replace;
    }

    public K withReplace(T replace) {
        this.replace = replace;
        this.hashCode = 0;
        return (K) this;
    }

    public List<String> getRemove() {
        return remove;
    }

    public K withRemove(List<String> remove) {
        this.remove = remove;
        this.hashCode = 0;
        return (K) this;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (hashCode() != o.hashCode()) {
            return false;
        }
        KeysToMultiValuesModifier<T, K, I> that = (KeysToMultiValuesModifier<T, K, I>) o;
        return Objects.equals(add, that.add) &&
            Objects.equals(replace, that.replace) &&
            Objects.equals(remove, that.remove);
    }

    @Override
    public int hashCode() {
        if (hashCode == 0) {
            hashCode = Objects.hash(add, replace, remove);
        }
        return hashCode;
    }

    public T update(T keysToMultiValues) {
        if (keysToMultiValues != null && replace.getEntries() != null) {
            replace.getEntries().forEach(keysToMultiValues::replaceEntryIfExists);
        }
        if (add.getEntries() != null) {
            if (keysToMultiValues != null) {
                add.getEntries().forEach(keysToMultiValues::withEntry);
            } else {
                return add.clone();
            }
        }
        if (keysToMultiValues != null && remove != null) {
            remove.forEach(keysToMultiValues::remove);
        }
        return keysToMultiValues;
    }
}
