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
    public void shouldConvertToHashMap() {
        // given
        Cookie cookie = new Cookie("name", "value");

        // when
        CaseInsensitiveRegexHashMap hashMap = KeyAndValue.toHashMap(cookie);

        // then
        assertThat(hashMap.get(string("name")), is(string("value")));
    }

    @Test
    public void shouldConvertNottedCookieToHashMap() {
        // given
        Cookie nottedCookie = new Cookie(not("name"), not("value"));

        // when
        CaseInsensitiveRegexHashMap hashMap = KeyAndValue.toHashMap(nottedCookie);

        // then
        assertThat(hashMap.get(not("name")), is(not("value")));
    }

    @Test
    public void shouldConvertListOfNottableCookiesToHashMap() {
        // given
        Cookie firstNottedCookie = new Cookie(not("name_one"), not("value_one"));
        Cookie secondCookie = new Cookie(string("name_two"), string("value_two"));

        // when
        CaseInsensitiveRegexHashMap hashMap = KeyAndValue.toHashMap(
                Arrays.asList(
                        firstNottedCookie,
                        secondCookie
                )
        );

        // then
        assertThat(hashMap.get(not("name_one")), is(not("value_one")));
        assertThat(hashMap.get(string("name_two")), is(string("value_two")));
    }

}