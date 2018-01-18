package org.mockserver.model;

import org.mockserver.client.serialization.ObjectMapperFactory;

/**
 * @author jamesdbloom
 */
public abstract class ObjectWithJsonToString extends ObjectWithReflectiveEqualsHashCodeToString {

    @Override
    public String toString() {
        try {
            String valueAsString = ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(this);
            if (valueAsString.startsWith("\"") && valueAsString.endsWith("\"")) {
                valueAsString = valueAsString
                    .replaceAll("^\"", "")
                    .replaceAll("\"$", "");
            }
            return valueAsString;
        } catch (Exception e) {
            return super.toString();
        }
    }
}
