package org.jamesdbloom.mockserver.client.serialization.model;

import org.codehaus.jackson.annotate.JsonCreator;
import org.codehaus.jackson.annotate.JsonProperty;
import org.jamesdbloom.mockserver.model.KeyToMultiValue;
import org.jamesdbloom.mockserver.model.Parameter;

/**
 * @author jamesdbloom
 */
public class ParameterDTO extends KeyToMultiValueDTO<String, String> {

    public ParameterDTO(Parameter parameter) {
        super(parameter);
    }
}
