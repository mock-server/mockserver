package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class NotDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    Boolean not;

    public NotDTO(boolean not) {
        if (not) {
            this.not = true;
        }
    }

    public NotDTO() {
    }

    public boolean isNot() {
        return not != null && not;
    }

    public Boolean getNot() {
        return not;
    }

    public void setNot(Boolean not) {
        this.not = not;
    }
}
