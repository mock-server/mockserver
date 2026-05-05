package org.mockserver.model;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockserver.model.PortBinding.portBinding;

public class PortBindingTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        PortBinding portBinding = new PortBinding();

        // then
        assertThat(portBinding.getPorts(), empty());
    }

    @Test
    public void shouldReturnValuesFromStaticBuilder() {
        // when
        PortBinding portBinding = portBinding(1, 2, 3);

        // then
        assertThat(portBinding.getPorts(), contains(1, 2, 3));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // when
        PortBinding portBinding = new PortBinding().setPorts(Arrays.asList(1, 2, 3));

        // then
        assertThat(portBinding.getPorts(), contains(1, 2, 3));
    }
}