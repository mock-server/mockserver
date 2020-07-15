package org.mockserver.collections.multimap.optionalmatcher;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
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
        NottableStringMultiMap matcher = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), string("keyThreeValue")}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        ).containsAll(matcher), is(false));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForOptionalKeysWithNottedValue() {
        // given
        NottableStringMultiMap matcher = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{optionalString("keyThree"), not("keyThreeValue")}
        );

        // then
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOtherThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeOtherValue")}
        ).containsAll(matcher), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForAllOptionalKeys() {
        // given
        NottableStringMultiMap matcher = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("?keyOne"), string("keyOneValue")},
            new NottableString[]{string("?keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("?keyThree"), string("keyThreeValue")}
        );

        // then - all three
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        // then - any two
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        // then - any single value
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyThree"), string("keyThreeValue")}
        ).containsAll(matcher), is(true));
        // then - empty
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[0][]
        ).containsAll(matcher), is(true));
        // then - other value
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("otherKey"), string("otherValue")}
        ).containsAll(matcher), is(true));
    }

    @Test
    public void shouldContainAllSubSetMultipleKeyForAllOptionalEitherOr() {
        // given
        NottableStringMultiMap matcher = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("?keyOne"), string("keyOneValue")},
            new NottableString[]{string("?keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOne|keyTwo"), string("key.*")}
        );

        // then - any two
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")},
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")},
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        // then - any single value
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("keyOneValue")}
        ).containsAll(matcher), is(true));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("keyTwoValue")}
        ).containsAll(matcher), is(true));
        // then - not
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("otherValue")}
        ).containsAll(matcher), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyTwo"), string("otherValue")}
        ).containsAll(matcher), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("otherKey"), string("otherValue")}
        ).containsAll(matcher), is(false));
        assertThat(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[0][]
        ).containsAll(matcher), is(false));
    }
}
