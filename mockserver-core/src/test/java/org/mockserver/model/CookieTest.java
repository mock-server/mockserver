package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.Cookie.cookie;

/**
 * @author jamesdbloom
 */
public class CookieTest {

    @Test
    public void shouldReturnValueSetInConstructors() {
        // when
        Cookie firstCookie = new Cookie("name", "value");

        // then
        assertThat(firstCookie.getName(), is("name"));
        assertThat(firstCookie.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructors() {
        // when
        Cookie firstCookie = cookie("name", "value");

        // then
        assertThat(firstCookie.getName(), is("name"));
        assertThat(firstCookie.getValue(), is("value"));
    }

}
