package org.mockserver.validator;

import org.mockserver.mock.Expectation;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jamesdbloom
 */
public class ExpectationValidator implements Validator<Expectation> {

    @Override
    public List<String> isValid(Expectation expectation) {
        List<String> validationErrors = new ArrayList<String>();

        if (expectation.getHttpRequest() == null) {
            validationErrors.add("no request matcher");
        }
        if (expectation.getHttpResponse() == null
                && expectation.getHttpForward() == null
                && expectation.getHttpClassCallback() == null
                && expectation.getHttpObjectCallback() == null
                && expectation.getHttpError() == null) {
            validationErrors.add("no response, forward, callback or error");
        }

        return validationErrors;
    }
}
