package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class ParameterTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        Parameter firstParameter = new Parameter("first", "first_one", "first_two");
        Parameter secondParameter = new Parameter("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstParameter.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(secondParameter.getValues(), containsInAnyOrder("second_one", "second_two"));
    }


}
