package org.mockserver.client.serialization.model;

import org.mockserver.model.ParameterBody;
import org.mockserver.model.Parameters;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTO extends BodyWithContentTypeDTO {

    private Parameters parameters;

    public ParameterBodyDTO(ParameterBody parameterBody) {
        this(parameterBody, false);
    }

    public ParameterBodyDTO(ParameterBody parameterBody, Boolean not) {
        super(parameterBody.getType(), not, parameterBody.getContentType());
        parameters = parameterBody.getValue();
    }

    protected ParameterBodyDTO() {
    }

    public Parameters getParameters() {
        return parameters;
    }

    public ParameterBody buildObject() {
        return new ParameterBody(parameters);
    }
}
