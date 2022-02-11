package org.mockserver.model;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
public abstract class KeysToMultiValuesModifier<T extends KeysToMultiValues<I, T>, K extends KeysToMultiValuesModifier<T, K, I>, I extends KeyToMultiValue> {

    private int hashCode;
    private T add;
    private T replace;
    private List<String> remove;

    abstract T construct(List<I> list);

    abstract T construct(I... array);

    public T getAdd() {
        return add;
    }

    public K withAdd(T add) {
        this.add = add;
        this.hashCode = 0;
        return (K) this;
    }

    public K add(List<I> add) {
        return withAdd(construct(add));
    }

    public K add(I... add) {
        return withAdd(construct(add));
    }

    public T getReplace() {
        return replace;
    }

    public K withReplace(T replace) {
        this.replace = replace;
        this.hashCode = 0;
        return (K) this;
    }

    public K replace(List<I> replace) {
        return withReplace(construct(replace));
    }

    public K replace(I... replace) {
        return withReplace(construct(replace));
    }

    public List<String> getRemove() {
        return remove;
    }

    public K withRemove(List<String> remove) {
        this.remove = remove;
        this.hashCode = 0;
        return (K) this;
    }

    public K remove(List<String> remove) {
        return withRemove(remove);
    }

    public K remove(String... remove) {
        return withRemove(Arrays.asList(remove));
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
        if (replace != null && replace.getEntries() != null && keysToMultiValues != null) {
            replace.getEntries().forEach(keysToMultiValues::replaceEntryIfExists);
        }
        if (add != null && add.getEntries() != null) {
            if (keysToMultiValues != null) {
                add.getEntries().forEach(keysToMultiValues::withEntry);
            } else {
                return add.clone();
            }
        }
        if (remove != null && keysToMultiValues != null) {
            remove.forEach(keysToMultiValues::remove);
        }
        return keysToMultiValues;
    }
}
