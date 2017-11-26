package org.mockserver.templates.engine.velocity.model;

import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.NottableString;
import org.mockserver.model.ObjectWithJsonToString;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValueTemplateObject extends ObjectWithJsonToString {
    private NottableString name;
    private List<NottableString> values;

    protected KeyToMultiValueTemplateObject(KeyToMultiValue keyToMultiValue) {
        name = keyToMultiValue.getName();
        values = keyToMultiValue.getValues();
    }

    protected KeyToMultiValueTemplateObject() {
    }

    public NottableString getName() {
        return name;
    }

    public List<NottableString> getValues() {
        return values;
    }
}
