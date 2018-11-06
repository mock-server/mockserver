package org.mockserver.serialization;

/**
 * @author jamesdbloom
 */
public interface Serializer<T> {

    String serialize(T t);

    T deserialize(String json);

    Class<T> supportsType();
}
