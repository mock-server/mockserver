package org.mockserver.serialization.model;

import org.mockserver.model.KeysAndValues;
import org.mockserver.model.KeysAndValuesModifier;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

@SuppressWarnings("unchecked")
public abstract class KeysAndValuesModifierDTO<T extends KeysAndValues<?, ?>, K extends KeysAndValuesModifier<T, K>, D extends DTO<K>> extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<K> {

    private T add;
    private T replace;
    private List<String> remove;

    public KeysAndValuesModifierDTO() {
    }

    public KeysAndValuesModifierDTO(K keysAndValuesModifier) {
        if (keysAndValuesModifier != null) {
            add = keysAndValuesModifier.getAdd();
            replace = keysAndValuesModifier.getReplace();
            remove = keysAndValuesModifier.getRemove();
        }
    }

    public K buildObject() {
        return newKeysAndValuesModifier()
            .withAdd(add)
            .withReplace(replace)
            .withRemove(remove);
    }

    abstract K newKeysAndValuesModifier();

    public T getAdd() {
        return add;
    }

    public D setAdd(T add) {
        this.add = add;
        return (D) this;
    }

    public T getReplace() {
        return replace;
    }

    public D setReplace(T replace) {
        this.replace = replace;
        return (D) this;
    }

    public List<String> getRemove() {
        return remove;
    }

    public D setRemove(List<String> remove) {
        this.remove = remove;
        return (D) this;
    }

}
