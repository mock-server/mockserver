package org.mockserver.client.serialization.model;

import org.mockserver.model.KeyToMultiValue;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyToMultiValueDTO extends NotDTO {
    private String name;
    private List<String> values;

    protected KeyToMultiValueDTO(KeyToMultiValue keyToMultiValue, Boolean not) {
        super(not);
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
