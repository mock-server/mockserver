package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Cookie;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class CookieDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        CookieDTO cookie = new CookieDTO(new Cookie("first", "first_one", "first_two"));

        // then
        assertThat(cookie.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(cookie.buildObject().getValues(), containsInAnyOrder("first_one", "first_two"));
    }
}
