package org.mockserver.collections.hashmap;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringHashMap.hashMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestAllKeysNottedOrOptional {

    @Test
    public void shouldReturnAllKeysOptional() {
        // true
        assertThat(hashMap(
            true, new String[]{"?keyOne", "keyOne_valueOne"},
            new String[]{"?keyTwo", "keyTwo_valueOne"},
            new String[]{"?keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(true));
        // false
        assertThat(hashMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"?keyTwo", "keyTwo_valueOne"},
            new String[]{"?keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(false));
        assertThat(hashMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne"},
            new String[]{"?keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(false));
        assertThat(hashMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne"},
            new String[]{"keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(false));
    }

    @Test
    public void shouldReturnAllKeysNotted() {
        // true
        assertThat(hashMap(
            true, new String[]{"!keyOne", "keyOne_valueOne"},
            new String[]{"!keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"!keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(true));
        // false
        assertThat(hashMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"!keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"!keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(false));
        assertThat(hashMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"!keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(false));
        assertThat(hashMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(false));
    }

}
