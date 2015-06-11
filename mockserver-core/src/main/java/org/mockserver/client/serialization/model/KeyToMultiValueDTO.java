package org.mockserver.client.serialization.model;

import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValueDTO extends ObjectWithReflectiveEqualsHashCodeToString {
    private NottableString name;
    private List<NottableString> values;

    protected KeyToMultiValueDTO(KeyToMultiValue keyToMultiValue) {
        name = keyToMultiValue.getName();
        values = keyToMultiValue.getValues();
    }

    protected KeyToMultiValueDTO() {
    }

    public NottableString getName() {
        return name;
    }

    public List<NottableString> getValues() {
        return values;
    }
}
