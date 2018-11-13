package org.mockserver.validator;

/**
 * @author jamesdbloom
 */
public interface Validator<T> {

    String isValid(T t);

}
