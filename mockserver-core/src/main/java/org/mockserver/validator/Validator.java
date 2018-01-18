package org.mockserver.validator;

/**
 * @author jamesdbloom
 */
public interface Validator<T> {

    public String isValid(T t);
}
