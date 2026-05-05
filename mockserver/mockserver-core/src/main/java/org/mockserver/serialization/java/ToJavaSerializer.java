package org.mockserver.serialization.java;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

/**
 * @author jamesdbloom
 */
public interface ToJavaSerializer<T extends ObjectWithReflectiveEqualsHashCodeToString> {

    String serialize(int numberOfSpacesToIndent, T object);

}
