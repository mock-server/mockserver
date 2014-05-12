package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class CookieTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        Cookie firstCookie = new Cookie("first", "first_one", "first_two");
        Cookie secondCookie = new Cookie("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstCookie.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(secondCookie.getValues(), containsInAnyOrder("second_one", "second_two"));
    }


}
