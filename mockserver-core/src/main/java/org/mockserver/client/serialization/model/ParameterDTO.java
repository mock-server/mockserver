package org.mockserver.client.serialization.model;

import org.mockserver.model.Not;
import org.mockserver.model.Parameter;

/**
 * @author jamesdbloom
 */
public class ParameterDTO extends KeyToMultiValueDTO implements DTO<Parameter> {

    public ParameterDTO(Parameter parameter) {
        super(parameter);
    }

    protected ParameterDTO() {
    }

    public Parameter buildObject() {
        return new Parameter(getName(), getValues());
    }
}
