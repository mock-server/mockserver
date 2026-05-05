package org.mockserver.serialization.model;

/**
 * @author jamesdbloom
 */
public interface DTO<T> {

    T buildObject();
}
