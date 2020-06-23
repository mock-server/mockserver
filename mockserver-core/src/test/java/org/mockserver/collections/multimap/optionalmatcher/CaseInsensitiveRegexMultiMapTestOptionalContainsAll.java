package org.mockserver.collections.multimap.optionalmatcher;

import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.CaseInsensitiveRegexMultiMap.multiMap;
import static org.mockserver.model.NottableOptionalString.optionalString;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestOptionalContainsAll {

    @Test
    public void shouldContainAllSubSetMultipleKeyForOptionalKeys() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue"), string("keyThree_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThree_valueTwo")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue"), string("keyThree_valueTwo")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThree_otherValueTwo")}
        )), is(false));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForOptionalKeysWithNottedValue() {
        // given
        CaseInsensitiveRegexMultiMap multiMap = multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), not("keyThreeValue")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        )), is(false));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOtherThree"), string("keyThreeValue")}
        )), is(true));
        assertThat(multiMap.containsAll(multiMap(true,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        )), is(true));
    }
}
