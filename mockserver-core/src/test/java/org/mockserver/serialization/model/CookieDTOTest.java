package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Cookie;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CookieDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        CookieDTO cookie = new CookieDTO(new Cookie("name", "value"));

        // then
        assertThat(cookie.getValue(), is(string("value")));
        assertThat(cookie.getName(), is(string("name")));
        assertThat(cookie.buildObject().getName(), is(string("name")));
        assertThat(cookie.buildObject().getValue(), is(string("value")));
    }
}
