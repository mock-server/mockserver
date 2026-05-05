package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.Body;
import org.mockserver.model.Parameter;
import org.mockserver.model.ParameterBody;
import org.mockserver.model.RegexBody;

import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.ParameterBody.params;

/**
 * @author jamesdbloom
 */
public class ParameterBodyDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        ParameterBodyDTO parameterBody = new ParameterBodyDTO(new ParameterBody(
            new Parameter("some", "value")
        ));

        // then
        assertThat(parameterBody.getParameters().getEntries(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        ParameterBody parameterBody = new ParameterBodyDTO(new ParameterBody(
            new Parameter("some", "value")
        )).buildObject();

        // then
        assertThat(parameterBody.getValue().getEntries(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
    }

    @Test
    public void shouldBuildCorrectObjectWithOptional() {
        // when
        ParameterBody parameterBody = new ParameterBodyDTO((ParameterBody) new ParameterBody(
            new Parameter("some", "value")
        ).withOptional(true)).buildObject();

        // then
        assertThat(parameterBody.getValue().getEntries(), containsInAnyOrder(new Parameter("some", "value")));
        assertThat(parameterBody.getType(), is(Body.Type.PARAMETERS));
        assertThat(parameterBody.getOptional(), is(true));
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
        assertThat(params(Collections.singletonList(
            new Parameter("some", "value")
            )),
            is(
                new ParameterBody(
                    new Parameter("some", "value")
                )
            )
        );
    }
}
