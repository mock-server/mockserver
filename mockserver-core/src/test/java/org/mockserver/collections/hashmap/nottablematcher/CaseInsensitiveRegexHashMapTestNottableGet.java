package org.mockserver.collections.hashmap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestNottableGet {

    @Test
    public void shouldGetSingeValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.get("keyT.*"), is(string("keyOneValue")));
        assertThat(hashMap.get(not("keyO.*")), is(string("keyOneValue")));
    }

    @Test
    public void shouldGetFirstMultiValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
            new NottableString[]{not("keyTwo"), string("keyTwoValue")},
            new NottableString[]{not("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.get("key.{1,4}e"), is(string("keyTwoValue")));
        assertThat(hashMap.get(not("keyTwo")), is(string("keyTwoValue")));
    }
}
