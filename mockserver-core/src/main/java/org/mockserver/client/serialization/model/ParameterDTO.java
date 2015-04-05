package org.mockserver.client.serialization.model;

import org.mockserver.model.Parameter;

/**
 * @author jamesdbloom
 */
public class ParameterDTO extends KeyToMultiValueDTO {

    public ParameterDTO(Parameter parameter, boolean not) {
        super(parameter, not);
    }

    protected ParameterDTO() {
    }

    public Parameter buildObject() {
        return new Parameter(getName(), getValues());
    }
}
