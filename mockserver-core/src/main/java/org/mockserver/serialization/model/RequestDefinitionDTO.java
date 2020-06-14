package org.mockserver.serialization.model;

import org.mockserver.model.RequestDefinition;

public abstract class RequestDefinitionDTO extends NotDTO {

    public RequestDefinitionDTO(Boolean not) {
        super(not);
    }

    public abstract RequestDefinition buildObject();

}
