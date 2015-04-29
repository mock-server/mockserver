package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class NotDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    Boolean not;

    public NotDTO(Boolean not) {
        if (not != null && not) {
            this.not = true;
        }
    }

    public NotDTO() {
    }

    public Boolean getNot() {
        return not;
    }

    public void setNot(Boolean not) {
        this.not = not;
    }
}
