package org.mockserver.client.serialization;

/**
 * @author jamesdbloom
 */
public interface Serializer<T> {

    public String serialize(T t);

    public T deserialize(String json);

    public Class<T> supportsType();
}
