package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class HeaderTest {

    @Test
    public void shouldReturnValuesSetInConstructors() {
        // when
        Header firstHeader = new Header("first", "first_one", "first_two");
        Header secondHeader = new Header("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstHeader.getValues(), containsInAnyOrder(string("first_one"), string("first_two")));
        assertThat(secondHeader.getValues(), containsInAnyOrder(string("second_one"), string("second_two")));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructors() {
        // when
        Header firstHeader = header("first", "first_one", "first_two");
        Header secondHeader = header("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstHeader.getValues(), containsInAnyOrder(string("first_one"), string("first_two")));
        assertThat(secondHeader.getValues(), containsInAnyOrder(string("second_one"), string("second_two")));
    }


}
