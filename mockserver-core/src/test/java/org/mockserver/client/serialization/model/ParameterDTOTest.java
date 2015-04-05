package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Parameter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

/**
 * @author jamesdbloom
 */
public class ParameterDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        ParameterDTO parameter = new ParameterDTO(new Parameter("first", "first_one", "first_two"), false);

        // then
        assertThat(parameter.getValues(), containsInAnyOrder("first_one", "first_two"));
        assertThat(parameter.buildObject().getValues(), containsInAnyOrder("first_one", "first_two"));
    }
}
