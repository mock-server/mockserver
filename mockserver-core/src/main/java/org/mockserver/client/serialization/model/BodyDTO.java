package org.mockserver.client.serialization.model;

import org.mockserver.model.*;

/**
 * @author jamesdbloom
 */
public abstract class BodyDTO extends NotDTO {

    private Body.Type type;

    public BodyDTO(Body.Type type, boolean not) {
        super(not);
        this.type = type;
    }

    public BodyDTO() {
    }

    public static BodyDTO createDTO(Body body) {
        if (body instanceof StringBody) {
            return new StringBodyDTO((StringBody) body);
        } else if (body instanceof RegexBody) {
            return new RegexBodyDTO((RegexBody) body);
        } else if (body instanceof JsonBody) {
            return new JsonBodyDTO((JsonBody) body);
        } else if (body instanceof JsonSchemaBody) {
            return new JsonSchemaBodyDTO((JsonSchemaBody) body);
        } else if (body instanceof XPathBody) {
            return new XPathBodyDTO((XPathBody) body);
        } else if (body instanceof ParameterBody) {
            return new ParameterBodyDTO((ParameterBody) body);
        } else if (body instanceof BinaryBody) {
            return new BinaryBodyDTO((BinaryBody) body);
        } else {
            return null;
        }
    }

    public Body.Type getType() {
        return type;
    }

    public abstract Body buildObject();

}
