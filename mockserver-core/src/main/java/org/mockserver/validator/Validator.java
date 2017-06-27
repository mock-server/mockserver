package org.mockserver.validator;

import java.util.List;

/**
 * @author jamesdbloom
 */
public interface Validator<T> {

    public String isValid(T t);
}
