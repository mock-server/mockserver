package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class HeaderTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        Header firstHeader = new Header("first", "first_one", "first_two");
        Header secondHeader = new Header("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstHeader.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(secondHeader.getValues(), containsInAnyOrder("second_one", "second_two"));
    }


}
