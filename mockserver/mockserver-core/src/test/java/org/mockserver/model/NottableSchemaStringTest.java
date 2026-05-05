package org.mockserver.model;

import org.junit.Test;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableSchemaString.notSchema;
import static org.mockserver.model.NottableSchemaString.schemaString;

public class NottableSchemaStringTest {

    @Test
    public void shouldReturnValuesSetInConstructors() {
        // when
        NottableSchemaString nottableString = notSchema("{ \"type\": \"string\" }");

        // then
        assertThat(nottableString.isNot(), is(true));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithDefaultNotSetting() {
        // when
        NottableSchemaString nottableString = schemaString("{ \"type\": \"string\" }");

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithDefaultNottedString() {
        // when
        NottableSchemaString nottableString = schemaString("!{ \"type\": \"string\" }");

        // then
        assertThat(nottableString.isNot(), is(true));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithNullNotParameter() {
        // when
        NottableSchemaString nottableString = schemaString("{ \"type\": \"string\" }", null);

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getValue(), is("{ \"type\": \"string\" }"));
    }

}
