package org.jamesdbloom.mockserver.model;

import org.jamesdbloom.mockserver.client.serialization.model.ParameterDTO;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue<String, String> {

    public Parameter(String name, String... value) {
        super(name, value);
    }

    public Parameter(ParameterDTO parameter) {
        super(parameter.getName(), parameter.getValues());
    }
}
