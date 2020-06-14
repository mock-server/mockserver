package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithJsonToString;

/**
 * @author jamesdbloom
 */
public class NotDTO extends ObjectWithJsonToString {

    Boolean not;

    public NotDTO(Boolean not) {
        this.not = not;
    }

    public NotDTO setNot(Boolean not) {
        this.not = not;
        return this;
    }

    public Boolean getNot() {
        return not;
    }

}
