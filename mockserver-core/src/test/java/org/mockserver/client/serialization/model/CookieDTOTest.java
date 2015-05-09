package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Cookie;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class CookieDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        CookieDTO cookie = new CookieDTO(new Cookie("name", "value"));

        // then
        assertThat(cookie.getValue(), is("value"));
        assertThat(cookie.getName(), is("name"));
        assertThat(cookie.buildObject().getName(), is("name"));
        assertThat(cookie.buildObject().getValue(), is("value"));
    }
}
