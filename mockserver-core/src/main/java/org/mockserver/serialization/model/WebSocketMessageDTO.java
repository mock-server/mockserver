package org.mockserver.serialization.model;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public class WebSocketMessageDTO extends ObjectWithReflectiveEqualsHashCodeToString {

    private String type;

    private String value;

    public String getType() {
        return type;
    }

    public WebSocketMessageDTO setType(String type) {
        this.type = type;
        return this;
    }

    public String getValue() {
        return value;
    }

    public WebSocketMessageDTO setValue(String value) {
        this.value = value;
        return this;
    }
}
