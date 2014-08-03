package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.ParameterBody.params;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        ParameterBodyDTO parameterBody = new ParameterBodyDTO(new ParameterBody(
                new Parameter("some", "value")
        ));

        // then
        assertThat(parameterBody.getParameters(), containsInAnyOrder(new ParameterDTO(new Parameter("some", "value"))));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        ParameterBody parameterBody = new ParameterBodyDTO(new ParameterBody(
                new Parameter("some", "value")
        )).buildObject();

        // then
        assertThat(parameterBody.getValue(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldReturnCorrectObjectFromStaticBuilder() {
        assertThat(params(
                        new Parameter("some", "value")
                ),
                is(
                        new ParameterBody(
                                new Parameter("some", "value")
                        )
                )
        );
        assertThat(params(Arrays.asList(
                        new Parameter("some", "value")
                )),
                is(
                        new ParameterBody(
                                new Parameter("some", "value")
                        )
                )
        );
    }

    @Test
    public void coverage() {
        new ParameterBodyDTO();
    }
}
