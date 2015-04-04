package org.mockserver.collections;

import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;
import static org.mockserver.collections.NottableKey.nottableKey;

public class NottableKeyTest {

    @Test
    public void shouldReturnValueSetInConstructors() {
        // when
        NottableKey nottableKey = nottableKey("value", true);

        // then
        assertThat(nottableKey.isNot(), is(true));
        assertThat(nottableKey.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValueSetInConstructorsWithDefaultNotSetting() {
        // when
        NottableKey nottableKey = nottableKey("value");

        // then
        assertThat(nottableKey.isNot(), is(false));
        assertThat(nottableKey.getValue(), is("value"));
    }

    @Test
    public void shouldReturnValueSetInConstructorsWithNullNotParameter() {
        // when
        NottableKey nottableKey = nottableKey("value", null);

        // then
        assertThat(nottableKey.isNot(), is(false));
        assertThat(nottableKey.getValue(), is("value"));
    }

    @Test
    public void shouldEqual() {
        assertThat(nottableKey("value"), is(nottableKey("value")));
        assertThat(nottableKey("value"), is((Object) "value"));
    }

    @Test
    public void shouldNotEqual() {
        assertThat(nottableKey("value", true), is(not(nottableKey("value"))));
        assertThat(nottableKey("value", true), is(not(nottableKey("value", true))));
        assertThat(nottableKey("value"), is(not(nottableKey("other_value"))));
        assertThat(nottableKey("value", true), is(not((Object) "value")));
        assertThat(nottableKey("value", false), is(not((Object) "other_value")));
    }

}