package org.mockserver.client.serialization.model;

import org.mockserver.model.KeyAndValue;
import org.mockserver.model.KeyToMultiValue;
import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

/**
 * @author jamesdbloom
 */
public class KeyAndValueDTO extends NotDTO {
    private String name;
    private String value;

    protected KeyAndValueDTO(KeyAndValue keyAndValue, boolean not) {
        super(not);
        name = keyAndValue.getName();
        value = keyAndValue.getValue();
    }

    protected KeyAndValueDTO() {
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }
}
