package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Parameter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class ParameterDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        ParameterDTO parameter = new ParameterDTO(new Parameter("first", "first_one", "first_two"));

        // then
        assertThat(parameter.getValues(), containsInAnyOrder(string("first_one"), string("first_two")));
        assertThat(parameter.buildObject().getValues(), containsInAnyOrder(string("first_one"), string("first_two")));
    }
}
