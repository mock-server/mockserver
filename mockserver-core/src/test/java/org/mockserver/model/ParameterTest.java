package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockserver.model.Parameter.param;

/**
 * @author jamesdbloom
 */
public class ParameterTest {

    @Test
    public void shouldReturnValuesSetInConstructors() {
        // when
        Parameter firstParameter = new Parameter("first", "first_one", "first_two");
        Parameter secondParameter = new Parameter("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstParameter.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(secondParameter.getValues(), containsInAnyOrder("second_one", "second_two"));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructors() {
        // when
        Parameter firstParameter = param("first", "first_one", "first_two");
        Parameter secondParameter = param("second", Arrays.asList("second_one", "second_two"));

        // then
        assertThat(firstParameter.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(secondParameter.getValues(), containsInAnyOrder("second_one", "second_two"));
    }


}
