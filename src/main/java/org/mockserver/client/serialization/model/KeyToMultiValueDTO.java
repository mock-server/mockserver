package org.mockserver.client.serialization.model;

import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.ModelObject;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValueDTO<K, V> extends ModelObject {
    private K name;
    private List<V> values;

    protected KeyToMultiValueDTO(KeyToMultiValue<K, V> keyToMultiValue) {
        name = keyToMultiValue.getName();
        values = keyToMultiValue.getValues();
    }

    protected KeyToMultiValueDTO() {
    }

    public K getName() {
        return name;
    }

    public List<V> getValues() {
        return values;
    }
}
