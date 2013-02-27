package org.jamesdbloom.mockserver.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.client.serialization.model.ParameterDTO;

/**
 * @author jamesdbloom
 */
public class Parameter extends KeyToMultiValue<String, String> {

    @JsonCreator
    public Parameter(@JsonProperty("name") String name, @JsonProperty("value") String... value) {
        super(name, value);
    }

    public Parameter(ParameterDTO parameter) {
        super(parameter.getName(), parameter.getValues());
    }
}
