package org.mockserver.client.serialization.model;

import org.mockserver.model.Not;
import org.mockserver.model.Parameter;

/**
 * @author jamesdbloom
 */
public class ParameterDTO extends KeyToMultiValueDTO {

    public ParameterDTO(Parameter parameter) {
        this(parameter, false);
    }

    public ParameterDTO(Parameter parameter, Boolean not) {
        super(parameter, not);
    }

    protected ParameterDTO() {
    }

    public Parameter buildObject() {
        return Not.not(new Parameter(getName(), getValues()), getNot());
    }
}
