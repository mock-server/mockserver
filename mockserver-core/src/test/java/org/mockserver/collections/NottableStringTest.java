package org.mockserver.collections;

import org.hamcrest.core.IsNot;
import org.junit.Test;
import org.mockserver.model.NottableString;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.NottableString.string;

public class NottableStringTest {

    @Test
    public void shouldReturnValuesSetInConstructors() {
        // when
        NottableString nottableString = NottableString.not("value");

        // then
        assertThat(nottableString.isNot(), is(true));
        assertThat(nottableString.getNot(), is(true));
        assertThat(nottableString.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithDefaultNotSetting() {
        // when
        NottableString nottableString = string("value");

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getNot(), nullValue());
        assertThat(nottableString.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithNullNotParameter() {
        // when
        NottableString nottableString = NottableString.string("value", null);

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getNot(), nullValue());
        assertThat(nottableString.getValue(), is("value"));
    }

    @Test
    public void shouldEqual() {
        assertThat(string("value"), is(string("value")));
        assertThat(NottableString.not("value"), is(NottableString.not("value")));
        assertThat(string("value"), is((Object) "value"));
    }

    @Test
    public void shouldEqualForDoubleNegative() {
        assertThat(NottableString.not("value"), IsNot.not(string("value")));
        assertThat(NottableString.not("value"), IsNot.not((Object) "value"));

        assertThat(string("value"), IsNot.not(string("other_value")));
        assertThat(NottableString.string("value"), IsNot.not((Object) "other_value"));

        assertThat(string("value"), IsNot.not(NottableString.not("value")));
    }

}