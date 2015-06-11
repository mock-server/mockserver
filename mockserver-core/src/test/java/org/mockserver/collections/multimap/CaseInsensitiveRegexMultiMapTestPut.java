package org.mockserver.collections.multimap;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestPut {

    @Test
    public void shouldPutSingleValueForNewKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(new String[]{});

        // when
        multiMap.put("keyOne", "keyOne_valueOne");

        // then
        assertThat(multiMap.size(), is(1));
        assertThat(multiMap.getAll("keyOne"), containsInAnyOrder(string("keyOne_valueOne")));
    }

    @Test
    public void shouldPutSingleValueForExistingKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyTwo", "keyTwo_valueOne"}
        );

        // when
        multiMap.put("keyTwo", "keyTwo_valueTwo");

        // then
        assertThat(multiMap.size(), is(1));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldPutSingleValueForExistingKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyTwo", "keyTwo_valueOne", "keyTwo_valueTwo"}
        );

        // when
        multiMap.put("keyTwo", "keyTwo_valueTwo");

        // then
        assertThat(multiMap.size(), is(1));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldPutSingleMultiValueForNewKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(new String[]{});

        // when
        multiMap.put("keyTwo", Arrays.asList("keyTwo_valueOne", "keyTwo_valueTwo"));

        // then
        assertThat(multiMap.size(), is(1));
        assertThat(multiMap.getAll("keyTwo"), containsInAnyOrder(string("keyTwo_valueOne"), string("keyTwo_valueTwo")));
    }

    @Test
    public void shouldPutSingleMultiValueForExistingKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyThree", "keyThree_valueOne"}
        );

        // when
        multiMap.put("keyThree", Arrays.asList("keyThree_valueTwo", "keyThree_valueThree"));

        // then
        assertThat(multiMap.size(), is(1));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
    }

    @Test
    public void shouldPutSingleMultiValueForExistingKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
                new String[]{"keyThree", "keyThree_valueOne", "keyThree_valueTwo"}
        );

        // when
        multiMap.put("keyThree", Arrays.asList("keyThree_valueTwo", "keyThree_valueThree"));

        // then
        assertThat(multiMap.size(), is(1));
        assertThat(multiMap.getAll("keyThree"), containsInAnyOrder(string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueTwo"), string("keyThree_valueThree")));
    }
}
