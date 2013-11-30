package org.mockserver.client.serialization.model;

import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.ModelObject;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValueDTO extends ModelObject {
    private String name;
    private List<String> values;

    protected KeyToMultiValueDTO(KeyToMultiValue keyToMultiValue) {
        name = keyToMultiValue.getName();
        values = keyToMultiValue.getValues();
    }

    protected KeyToMultiValueDTO() {
    }

    public String getName() {
        return name;
    }

    public List<String> getValues() {
        return values;
    }
}
