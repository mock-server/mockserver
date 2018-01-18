package org.mockserver.model;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

public class KeyAndValueTest {

    @Test
    public void shouldConvertNottedCookieToHashMap() {
        // given
        KeyAndValue keyAndValue = new KeyAndValue("name", "value");

        // then
        assertThat(keyAndValue.getName(), is(string("name")));
        assertThat(keyAndValue.getValue(), is(string("value")));
    }

}
