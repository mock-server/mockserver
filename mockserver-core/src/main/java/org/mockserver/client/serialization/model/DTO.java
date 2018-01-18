package org.mockserver.client.serialization.model;

/**
 * @author jamesdbloom
 */
public interface DTO<T> {

    T buildObject();
}
