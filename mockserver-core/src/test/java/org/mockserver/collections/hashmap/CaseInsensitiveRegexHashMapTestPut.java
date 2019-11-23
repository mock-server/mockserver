package org.mockserver.collections.hashmap;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestPut {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void shouldPutSingleValueForNewKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(true, new String[]{});

        // when
        hashMap.put("keyOne", "keyOneValue");

        // then
        assertThat(hashMap.size(), is(1));
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
    }

    @Test
    public void shouldPutSingleValueForExistingKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // when
        hashMap.put("keyTwo", "keyTwoValue");

        // then
        assertThat(hashMap.size(), is(2));
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
        assertThat(hashMap.get("keyTwo"), is(string("keyTwoValue")));
    }

    @Test
    public void shouldPutSingleValueForExistingKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new String[]{"keyOne", "keyOneValue"}
        );

        // when
        hashMap.put("keyOne", "keyOneValue");

        // then
        assertThat(hashMap.size(), is(1));
        assertThat(hashMap.get("keyOne"), is(string("keyOneValue")));
    }
}
