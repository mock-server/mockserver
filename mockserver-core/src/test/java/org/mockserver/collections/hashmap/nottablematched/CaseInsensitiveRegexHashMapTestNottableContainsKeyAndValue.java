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
public class CaseInsensitiveRegexHashMapTestNottableContainsKeyAndValue {

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("notKeyOne"), string("keyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("notKeyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldContainKeyAndValueForSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("notKeyOne"), not("notKeyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedKey() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("notKeyOne"), string("keyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("notKeyOneValue")), is(true));
    }

    @Test
    public void multiValuedMapShouldContainKeyAndValueForSingleValueForNottedKeyAndValue() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("notKeyOne"), not("notKeyOneValue")), is(true));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("keyOne"), string("keyOneValue")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("keyOneValue")), is(false));
    }

    @Test
    public void singleValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyAndValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("keyOne"), not("keyOneValue")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForSingleValueWithKeyMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("keyOne"), string("keyOneValue")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForSingleValueWithValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(string("keyOne"), not("keyOneValue")), is(false));
    }

    @Test
    public void multiValuedMapShouldNotContainKeyAndValueForMultipleValuesWithKeyAndValueMismatch() {
        // given
        CaseInsensitiveRegexHashMap hashMap = hashMap(
                new String[]{"keyOne", "keyOneValue"},
                new String[]{"keyTwo", "keyTwoValue"},
                new String[]{"keyThree", "keyThreeValue"}
        );

        // then
        assertThat(hashMap.containsKeyValue(not("key.*"), not("key.*")), is(false));
    }
}
