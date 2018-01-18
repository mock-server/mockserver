package org.mockserver.client.serialization.model;

import org.mockserver.model.KeyAndValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class KeyAndValueDTO extends ObjectWithReflectiveEqualsHashCodeToString {
    private NottableString name;
    private NottableString value;

    protected KeyAndValueDTO(KeyAndValue keyAndValue) {
        name = keyAndValue.getName();
        value = keyAndValue.getValue();
    }

    protected KeyAndValueDTO() {
    }

    public NottableString getName() {
        return name;
    }

    public NottableString getValue() {
        return value;
    }
}
