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
public class CaseInsensitiveRegexHashMapTestNottableContainsAll {

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("notKeyOne"), string("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("notKeyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("notKeyOne"), string("notKeyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("notKeyOne"), string("keyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("notKeyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("notKeyOne"), string("notKeyOneValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("notKeyTwo"), string("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("notKeyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("notKeyTwo"), string("notKeyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("notKeyTwo"), string("keyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), not("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("notKeyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("notKeyTwo"), string("notKeyTwoValue")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldContainAllEmptySubSetMultipleKeyAndMultipleValuesForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), not("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForEmptyMap() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")}
        )), is(true));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeySingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyAndValueSingleEntry() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleValueMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchSingleKeyAndValueMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), not("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleKeysMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyTwo"), string("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntries() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), not("keyOneValue")},
                new NottableString[]{string("keyTwo"), not("keyTwoValue")},
                new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(false));
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyTwo"), string("keyTwo.*")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllNotMatchMultipleValuesMultipleEntriesContradiction() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{string("keyOne"), string("keyOneValue")},
                new NottableString[]{not("keyOne"), not("keyOneValue")}
        )), is(false));
    }

    @Test
    public void shouldNotContainAllSubSetMultipleKeyForEmptyMap() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new NottableString[]{}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
                new NottableString[]{not("keyOne"), string("keyOneValue")},
                new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(false));
    }
}
