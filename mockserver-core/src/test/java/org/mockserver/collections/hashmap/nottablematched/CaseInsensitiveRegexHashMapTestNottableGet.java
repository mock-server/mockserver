package org.mockserver.collections.hashmap.nottablematched;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;

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
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.get(not("keyTwo")), is(string("keyOneValue")));
    }

    @Test
    public void shouldGetFirstMultiValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            new String[]{"keyOne", "keyOneValue"},
            new String[]{"keyTwo", "keyTwoValue"}
        );

        // then
        assertThat(hashMap.get(not("keyOne")), is(string("keyTwoValue")));
    }
}
