package org.mockserver.serialization.java;

import org.mockserver.model.ObjectWithReflectiveEqualsHashCodeToString;

import java.util.List;

/**
 * @author jamesdbloom
 */
public interface MultiValueToJavaSerializer<T extends ObjectWithReflectiveEqualsHashCodeToString> extends ToJavaSerializer<T> {

    String serializeAsJava(int numberOfSpacesToIndent, List<T> object);

    @SuppressWarnings("unchecked")
    String serializeAsJava(int numberOfSpacesToIndent, T... object);

}
