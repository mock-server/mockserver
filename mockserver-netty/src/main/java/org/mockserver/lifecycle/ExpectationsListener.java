package org.mockserver.lifecycle;

import org.mockserver.mock.Expectation;

import java.util.List;

/**
 * @author jamesdbloom
 */
public interface ExpectationsListener {

    void updated(List<Expectation> expectations);

}
