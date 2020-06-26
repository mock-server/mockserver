package org.mockserver.collections.multimap;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestAllKeysNottedOrOptional {

    @Test
    public void shouldReturnAllKeysOptional() {
        // true
        assertThat(multiMap(
            true, new String[]{"?keyOne", "keyOne_valueOne"},
            new String[]{"?keyTwo", "keyTwo_valueOne"},
            new String[]{"?keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(true));
        // false
        assertThat(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"?keyTwo", "keyTwo_valueOne"},
            new String[]{"?keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(false));
        assertThat(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne"},
            new String[]{"?keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(false));
        assertThat(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne"},
            new String[]{"keyThree", "keyThree_valueOne"}
        ).allKeysOptional(), is(false));
    }

    @Test
    public void shouldReturnAllKeysNotted() {
        // true
        assertThat(multiMap(
            true, new String[]{"!keyOne", "keyOne_valueOne"},
            new String[]{"!keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"!keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(true));
        // false
        assertThat(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"!keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"!keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(false));
        assertThat(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"!keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(false));
        assertThat(multiMap(
            true, new String[]{"keyOne", "keyOne_valueOne"},
            new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"},
            new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo", "keyThree_valueThree"}
        ).allKeysNotted(), is(false));
    }

}
