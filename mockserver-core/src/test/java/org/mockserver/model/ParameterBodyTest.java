package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.ParameterBody.params;

/**
 * @author jamesdbloom
 */
public class ParameterBodyTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        ParameterBody parameterBody = new ParameterBody(
                new Parameter("some", "value")
        );

        // then
        assertThat(parameterBody.getValue(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldReturnValuesSetInConstructorWithList() {
        // when
        ParameterBody parameterBody = new ParameterBody(Arrays.asList(
                new Parameter("some", "value")
        ));

        // then
        assertThat(parameterBody.getValue(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructor() {
        // when
        ParameterBody parameterBody = params(
                new Parameter("some", "value")
        );

        // then
        assertThat(parameterBody.getValue(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldReturnValueSetInStaticConstructorWithList() {
        // when
        ParameterBody parameterBody = params(Arrays.asList(
                new Parameter("some", "value")
        ));

        // then
        assertThat(parameterBody.getValue(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

}
