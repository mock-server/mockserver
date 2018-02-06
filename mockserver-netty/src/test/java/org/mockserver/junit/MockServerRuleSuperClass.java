package org.mockserver.junit;

import org.mockserver.client.MockServerClient;

import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;

/**
 * @author jamesdbloom
 */
public class MockServerRuleSuperClass {

    MockServerClient mockServerClient;

}
