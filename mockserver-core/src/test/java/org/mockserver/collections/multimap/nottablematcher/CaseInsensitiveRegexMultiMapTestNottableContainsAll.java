package org.mockserver.collections.multimap.nottablematcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexHashMap;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexHashMap.hashMap;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestNottableContainsAll {

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyOne"), string("keyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyOne"), string("keyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyOne"), string("notKeyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("keyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("notKeyTwo"), string("notKeyTwo_valueOne"), string("notKeyTwo_valueTwo")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllEmptySubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForEmptyMap() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
            false, new NottableString[]{}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            false, new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyAndValueSingleEntry() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyAndValueMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{not("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), not("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), not("keyTwo_valueOne"), not("keyTwo_valueTwo")},
                new NottableString[]{string("keyThree"), string("keyThree_valueOne"), string("keyThree_valueTwo"), string("keyThree_valueThree")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOne_valueOne")},
                new NottableString[]{string("keyTwo"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyTwo"), string("keyTwo.*")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntriesContradiction() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllSubSetMultipleKeyForEmptyMap() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(
            true, new NottableString[]{}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true, new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(false));
    }
}
