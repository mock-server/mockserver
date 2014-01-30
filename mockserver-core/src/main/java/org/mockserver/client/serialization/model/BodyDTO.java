package org.mockserver.client.serialization.model;

import org.mockserver.model.Body;
import org.mockserver.model.EqualsHashCodeToString;
import org.mockserver.model.ParameterBody;
import org.mockserver.model.StringBody;

/**
 * @author jamesdbloom
 */
public abstract class BodyDTO extends EqualsHashCodeToString {

    private Body.Type type;

    public BodyDTO(Body.Type type) {
        this.type = type;
    }

    public BodyDTO() {
    }

    public static BodyDTO createDTO(Body body) {
        if (body instanceof StringBody) {
            return new StringBodyDTO((StringBody) body);
        } else if (body instanceof ParameterBody) {
            return new ParameterBodyDTO((ParameterBody) body);
        } else {
            return null;
        }
    }

    public Body.Type getType() {
        return type;
    }

    public abstract Body buildObject();

}
