package org.jamesdbloom.mockserver.client.serialization.model;

import org.jamesdbloom.mockserver.model.Parameter;

/**
 * @author jamesdbloom
 */
public class ParameterDTO extends KeyToMultiValueDTO<String, String> {

    public ParameterDTO(Parameter parameter) {
        super(parameter);
    }

    public ParameterDTO() {
    }
}
