package org.mockserver.validator;

import java.util.List;

/**
 * @author jamesdbloom
 */
public interface Validator<T> {

    public List<String> isValid(T t);
}
