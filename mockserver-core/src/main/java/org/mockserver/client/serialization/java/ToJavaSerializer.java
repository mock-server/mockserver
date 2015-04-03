package org.mockserver.client.serialization.java;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public interface ToJavaSerializer<T extends ObjectWithReflectiveEqualsHashCodeToString> {

    public String serializeAsJava(int numberOfSpacesToIndent, T object);

}
