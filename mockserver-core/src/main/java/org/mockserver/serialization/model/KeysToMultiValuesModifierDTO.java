package org.mockserver.serialization.model;

import org.mockserver.model.KeysToMultiValues;
import org.mockserver.model.KeysToMultiValuesModifier;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

@SuppressWarnings("unchecked")
public abstract class KeysToMultiValuesModifierDTO<T extends KeysToMultiValues<?, ?>, K extends KeysToMultiValuesModifier<T, K>, D extends DTO<K>> extends ObjectWithReflectiveEqualsHashCodeToString implements DTO<K> {

    private T add;
    private T replace;
    private List<String> remove;

    public KeysToMultiValuesModifierDTO() {
    }

    public KeysToMultiValuesModifierDTO(K keysToMultiValuesModifier) {
        if (keysToMultiValuesModifier != null) {
            add = keysToMultiValuesModifier.getAdd();
            replace = keysToMultiValuesModifier.getReplace();
            remove = keysToMultiValuesModifier.getRemove();
        }
    }

    public K buildObject() {
        return newKeysToMultiValuesModifier()
            .withAdd(add)
            .withReplace(replace)
            .withRemove(remove);
    }

    abstract K newKeysToMultiValuesModifier();

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
