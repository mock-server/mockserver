package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;

/**
 * Pins the semantics of the {@link HttpRequest#matches} overloads: the method must equal
 * the given method AND the path must equal one of the given paths. The one-, two- and three-arg
 * overloads and the varargs form must all agree, and the method is tested once (a method mismatch
 * short-circuits before any path comparison).
 */
public class HttpRequestMatchesTest {

    private HttpRequest putExpectation() {
        return request().withMethod("PUT").withPath("/mockserver/expectation");
    }

    @Test
    public void methodOnlyOverload() {
        assertThat(putExpectation().matches("PUT"), is(true));
        assertThat(putExpectation().matches("GET"), is(false));
    }

    @Test
    public void singlePathOverload() {
        assertThat(putExpectation().matches("PUT", "/mockserver/expectation"), is(true));
        assertThat(putExpectation().matches("PUT", "/other"), is(false));
        assertThat("method mismatch is false regardless of path",
            putExpectation().matches("GET", "/mockserver/expectation"), is(false));
    }

    @Test
    public void twoPathOverloadMatchesEitherPath() {
        assertThat(putExpectation().matches("PUT", "/mockserver/expectation", "/expectation"), is(true));
        assertThat(putExpectation().matches("PUT", "/expectation", "/mockserver/expectation"), is(true));
        assertThat(putExpectation().matches("PUT", "/a", "/b"), is(false));
        assertThat("method mismatch is false even when a path would match",
            putExpectation().matches("GET", "/mockserver/expectation", "/expectation"), is(false));
    }

    @Test
    public void varargsOverloadMatchesAnyPath() {
        assertThat(putExpectation().matches("PUT", "/a", "/b", "/mockserver/expectation"), is(true));
        assertThat(putExpectation().matches("PUT", "/a", "/b", "/c"), is(false));
        assertThat("varargs with no paths never matches", putExpectation().matches("PUT", new String[0]), is(false));
        assertThat("method mismatch is false even when a path would match",
            putExpectation().matches("GET", "/a", "/mockserver/expectation", "/c"), is(false));
    }

    @Test
    public void bareAliasForm() {
        HttpRequest bare = request().withMethod("PUT").withPath("/expectation");
        assertThat(bare.matches("PUT", "/mockserver/expectation", "/expectation"), is(true));
    }
}
