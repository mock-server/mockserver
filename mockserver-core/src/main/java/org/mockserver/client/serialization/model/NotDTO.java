package org.mockserver.client.serialization.model;

import org.mockserver.model.ObjectWithJsonToString;

/**
 * @author jamesdbloom
 */
public class NotDTO extends ObjectWithJsonToString {

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
