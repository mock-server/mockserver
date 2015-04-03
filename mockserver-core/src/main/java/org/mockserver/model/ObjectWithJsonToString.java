package org.mockserver.model;

import org.mockserver.client.serialization.ObjectMapperFactory;

/**
 * @author jamesdbloom
 */
public abstract class ObjectWithJsonToString extends ObjectWithReflectiveEqualsHashCodeToString {

    @Override
    public String toString() {
        try {
            return ObjectMapperFactory
                    .createObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(this);
        } catch (Exception e) {
            return super.toString();
        }
    }
}
