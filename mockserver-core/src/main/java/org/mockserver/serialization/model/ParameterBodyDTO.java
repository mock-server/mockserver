package org.mockserver.serialization.model;

import org.mockserver.model.ParameterBody;
import org.mockserver.model.Parameters;

import java.util.Objects;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTO extends BodyWithContentTypeDTO {

    private final Parameters parameters;

    public ParameterBodyDTO(ParameterBody parameterBody) {
        this(parameterBody, null);
    }

    public ParameterBodyDTO(ParameterBody parameterBody, Boolean not) {
        super(parameterBody.getType(), not, parameterBody.getContentType());
        parameters = parameterBody.getValue();
    }

    public Parameters getParameters() {
        return parameters;
    }

    public ParameterBody buildObject() {
        return new ParameterBody(parameters);
    }

}
