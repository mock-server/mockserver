package org.jamesdbloom.mockserver.client.serialization.model;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.jamesdbloom.mockserver.model.KeyToMultiValue;
import org.jamesdbloom.mockserver.model.ModelObject;

import java.util.Arrays;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValueDTO<K, V> extends ModelObject {
    private K name;
    private List<V> values;

    protected KeyToMultiValueDTO(KeyToMultiValue<K, V> keyToMultiValue) {
        this.name = keyToMultiValue.getName();
        this.values = keyToMultiValue.getValues();
    }

    public K getName() {
        return name;
    }

    public void setName(K name) {
        this.name = name;
    }

    public List<V> getValues() {
        return values;
    }

    public void setValues(List<V> values) {
        this.values = values;
    }
}
