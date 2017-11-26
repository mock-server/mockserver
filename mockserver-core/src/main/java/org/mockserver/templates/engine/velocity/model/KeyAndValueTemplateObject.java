package org.mockserver.templates.engine.velocity.model;

import org.mockserver.model.KeyAndValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class KeyAndValueTemplateObject extends ObjectWithReflectiveEqualsHashCodeToString {
    private NottableString name;
    private NottableString value;

    KeyAndValueTemplateObject(KeyAndValue keyAndValue) {
        name = keyAndValue.getName();
        value = keyAndValue.getValue();
    }

    public NottableString getName() {
        return name;
    }

    public NottableString getValue() {
        return value;
    }
}
