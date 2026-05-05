package org.mockserver.model;

import org.junit.Test;

import static junit.framework.TestCase.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockserver.model.NottableString.string;

public class NottableStringTest {

    @Test
    public void shouldReturnValuesSetInConstructors() {
        // when
        NottableString nottableString = NottableString.not("value");

        // then
        assertThat(nottableString.isNot(), is(true));
        assertThat(nottableString.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithDefaultNotSetting() {
        // when
        NottableString nottableString = string("value");

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValuesSetInConstructorsWithNullNotParameter() {
        // when
        NottableString nottableString = NottableString.string("value", null);

        // then
        assertThat(nottableString.isNot(), is(false));
        assertThat(nottableString.getValue(), is("value"));
    }

    @Test
    public void shouldEqual() {
        assertThat(string("value"), is(string("value")));
        assertThat(NottableString.not("value"), is(NottableString.not("value")));
        assertThat(string("value"), is((Object) "value"));
    }

    @Test
    public void shouldEqualIgnoreCase() {
        assertTrue(string("value").equalsIgnoreCase(string("VALUE")));
        assertTrue(NottableString.not("value").equalsIgnoreCase(NottableString.not("vaLUe")));
        assertTrue(string("value").equalsIgnoreCase("VaLue"));
    }

    @Test
    public void shouldEqualWhenNull() {
        assertThat(string(null), is(string(null)));
        assertThat(string("value"), not(string(null)));
        assertThat(string(null), not(string("value")));
    }

    @Test
    public void shouldEqualForDoubleNegative() {
        assertThat(NottableString.not("value"), not(string("value")));
        assertThat(NottableString.not("value"), not((Object) "value"));

        assertThat(string("value"), not(string("other_value")));
        assertThat(NottableString.string("value"), not((Object) "other_value"));

        assertThat(string("value"), not(NottableString.not("value")));
    }

    @Test
    public void shouldEqualForDoubleNegativeIgnoreCase() {
        assertFalse(NottableString.not("value").equalsIgnoreCase(string("VAlue")));
        assertFalse(NottableString.not("value").equalsIgnoreCase("vaLUe"));

        assertFalse(string("value").equalsIgnoreCase(string("other_value")));
        assertFalse(NottableString.string("value").equalsIgnoreCase("OTHER_value"));

        assertFalse(string("value").equalsIgnoreCase(NottableString.not("VALUE")));
    }

    @Test
    public void shouldEqualForNotValueNull() {
        assertEquals(NottableString.not("value"), string("value", true));
        assertEquals(string("value"), string("value", false));

        NottableString initiallyTrueValue = NottableString.string("value");
        assertEquals(initiallyTrueValue, string("value", null));
        assertFalse(string("value", null).isNot());
    }

    @Test
    public void shouldConvertToString() {
        assertThat(NottableString.not("value").toString(), is("!value"));
        assertThat("" + NottableString.not("value"), is("!value"));
        assertThat(String.valueOf(NottableString.not("value")), is("!value"));

        assertThat(NottableString.string("value").toString(), is("value"));
        assertThat("" + NottableString.string("value"), is("value"));
        assertThat(String.valueOf(NottableString.string("value")), is("value"));
    }

}
