package org.mockserver.serialization.model;

import org.mockserver.model.ParameterBody;
import org.mockserver.model.Parameters;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTO extends BodyDTO {

    private final Parameters parameters;

    public ParameterBodyDTO(ParameterBody parameterBody) {
        this(parameterBody, null);
    }

    public ParameterBodyDTO(ParameterBody parameterBody, Boolean not) {
        super(parameterBody.getType(), not);
        parameters = parameterBody.getValue();
        withOptional(parameterBody.getOptional());
    }

    public Parameters getParameters() {
        return parameters;
    }

    public ParameterBody buildObject() {
        return new ParameterBody(parameters);
    }

}
