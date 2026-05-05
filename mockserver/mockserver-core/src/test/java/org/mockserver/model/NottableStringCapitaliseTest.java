package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

public class NottableStringCapitaliseTest {

    @Test
    public void shouldCapitalisePlainValues() {
        assertThat(NottableString.not("value").capitalize(), is(NottableString.not("Value")));
        assertThat(NottableString.string("value").capitalize(), is(NottableString.string("Value")));

        assertThat(NottableString.not("Value").capitalize(), is(NottableString.not("Value")));
        assertThat(NottableString.string("Value").capitalize(), is(NottableString.string("Value")));
    }

    @Test
    public void shouldCapitaliseValuesWithDash() {
        assertThat(NottableString.not("value-value").capitalize(), is(NottableString.not("Value-Value")));
        assertThat(NottableString.string("value-value").capitalize(), is(NottableString.string("Value-Value")));

        assertThat(NottableString.not("Value-Value").capitalize(), is(NottableString.not("Value-Value")));
        assertThat(NottableString.string("Value-Value").capitalize(), is(NottableString.string("Value-Value")));
    }

    @Test
    public void shouldCapitaliseValuesWithDashAtStart() {
        assertThat(NottableString.not("-value-value").capitalize(), is(NottableString.not("-Value-Value")));
        assertThat(NottableString.string("-value-value").capitalize(), is(NottableString.string("-Value-Value")));

        assertThat(NottableString.not("-Value-Value").capitalize(), is(NottableString.not("-Value-Value")));
        assertThat(NottableString.string("-Value-Value").capitalize(), is(NottableString.string("-Value-Value")));
    }

    @Test
    public void shouldCapitaliseValuesWithDashAtEnd() {
        assertThat(NottableString.not("value-value-").capitalize(), is(NottableString.not("Value-Value-")));
        assertThat(NottableString.string("value-value-").capitalize(), is(NottableString.string("Value-Value-")));

        assertThat(NottableString.not("Value-Value-").capitalize(), is(NottableString.not("Value-Value-")));
        assertThat(NottableString.string("Value-Value-").capitalize(), is(NottableString.string("Value-Value-")));
    }

    @Test
    public void shouldCapitaliseValuesWithDashAtStartAndEnd() {
        assertThat(NottableString.not("-value-value-").capitalize(), is(NottableString.not("-Value-Value-")));
        assertThat(NottableString.string("-value-value-").capitalize(), is(NottableString.string("-Value-Value-")));

        assertThat(NottableString.not("-Value-Value-").capitalize(), is(NottableString.not("-Value-Value-")));
        assertThat(NottableString.string("-Value-Value-").capitalize(), is(NottableString.string("-Value-Value-")));
    }

    @Test
    public void shouldCapitaliseValuesWithMultipleTouchingDashes() {
        assertThat(NottableString.not("value--value").capitalize(), is(NottableString.not("Value--Value")));
        assertThat(NottableString.string("value--value").capitalize(), is(NottableString.string("Value--Value")));

        assertThat(NottableString.not("Value--Value").capitalize(), is(NottableString.not("Value--Value")));
        assertThat(NottableString.string("Value--Value").capitalize(), is(NottableString.string("Value--Value")));
    }

    @Test
    public void shouldLowercaseValues() {
        assertThat(NottableString.not("Value").lowercase(), is(NottableString.not("value")));
        assertThat(NottableString.string("valuE").lowercase(), is(NottableString.string("value")));

        assertThat(NottableString.not("VALUE").lowercase(), is(NottableString.not("value")));
        assertThat(NottableString.string("value").lowercase(), is(NottableString.string("value")));
    }

}