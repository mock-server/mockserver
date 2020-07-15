package org.mockserver.collections.multimap.schemamatcher;

import org.junit.Test;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.model.KeyMatchStyle;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.collections.NottableStringMultiMap.multiMap;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTestSchemaContainsAll {
    String stringLengthSchema = "{" + NEW_LINE +
        "  \"type\": \"string\"," + NEW_LINE +
        "  \"minLength\": 2," + NEW_LINE +
        "  \"maxLength\": 3" + NEW_LINE +
        "}";
    String emailPatternSchema = "{" + NEW_LINE +
        "    \"type\": \"string\"," + NEW_LINE +
        "    \"format\": \"email\"" + NEW_LINE +
        "}";

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("keyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("keyOne_valueOne")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("abc"), string("keyOne_valueOne")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("abc")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("a")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("abc")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("a")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("a")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("a")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("abc")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("abc")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), not("a")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("a")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("abc")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")},
            new NottableString[]{schemaString(emailPatternSchema), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("keyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("keyOne_valueOne")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("abc"), string("keyOne_valueOne")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)},
            new NottableString[]{string("keyTwo"), schemaString(emailPatternSchema)}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("abc")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("a")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("abc")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("a")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)},
            new NottableString[]{schemaString(emailPatternSchema), schemaString(emailPatternSchema)}
        );

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("a")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("a")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("abc")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("abc")}
        )), is(false));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), not("a")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("a")}
        )), is(true));

        // and then
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("abc")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForSchemaKey() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")},
            new NottableString[]{schemaString(emailPatternSchema), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then - matches
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then - key string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueTwo")}
        )), is(false));

        // and then - key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("keyOne_valueOne")},
            new NottableString[]{string("abcdef"), string("keyTwo_valueTwo")}
        )), is(false));

        // and then - both key string length and key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("keyOne_valueOne")},
            new NottableString[]{string("abcdef"), string("keyTwo_valueTwo")}
        )), is(false));

        // NOT-ED

        // then - doesn't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("abc"), string("keyOne_valueOne")},
            new NottableString[]{not("someone@mockserver.com"), string("keyTwo_valueTwo")}
        )), is(false));

        // and then - key string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then - key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("keyOne_valueOne")},
            new NottableString[]{not("abcdef"), string("keyTwo_valueTwo")}
        )), is(true));

        // and then - both key string length and key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("keyOne_valueOne")},
            new NottableString[]{not("abcdef"), string("keyTwo_valueTwo")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForSchemaValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)},
            new NottableString[]{string("keyTwo"), schemaString(emailPatternSchema)}
        );

        // then - matches
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("abc")},
            new NottableString[]{string("keyTwo"), string("someone@mockserver.com")}
        )), is(true));

        // and then - key string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("a")},
            new NottableString[]{string("keyTwo"), string("someone@mockserver.com")}
        )), is(false));

        // and then - key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("abc")},
            new NottableString[]{string("keyTwo"), string("abcdef")}
        )), is(false));

        // and then - both key string length and key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("a")},
            new NottableString[]{string("keyTwo"), string("abcdef")}
        )), is(false));

        // NOT-ED

        // then - doesn't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("abc")},
            new NottableString[]{string("keyTwo"), not("someone@mockserver.com")}
        )), is(false));

        // and then - value string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("a")},
            new NottableString[]{string("keyTwo"), string("someone@mockserver.com")}
        )), is(true));

        // and then - value email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), string("abc")},
            new NottableString[]{string("keyTwo"), not("abcdef")}
        )), is(true));

        // and then - both value string length and value email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("keyOne"), not("a")},
            new NottableString[]{string("keyTwo"), not("abcdef")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndMultipleValuesForSchemaKeyAndValue() {
        // given
        NottableStringMultiMap multiMap = multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)},
            new NottableString[]{schemaString(emailPatternSchema), schemaString(emailPatternSchema)}
        );

        // then - matches
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(true));

        // key and value don't match

        // and then - both string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("a")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(false));

        // and then - both emails don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{string("abcdef"), string("abcdef")}
        )), is(false));

        // and then - both string lengths and emails don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("a")},
            new NottableString[]{string("abcdef"), string("abcdef")}
        )), is(false));

        // key don't match

        // and then - key string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(false));

        // and then - key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{string("abcdef"), string("someone@mockserver.com")}
        )), is(false));

        // and then - both key string length and key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("a"), string("abc")},
            new NottableString[]{string("abcdef"), string("someone@mockserver.com")}
        )), is(false));


        // value don't match

        // and then - key string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("a")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(false));

        // and then - key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("abcdef")}
        )), is(false));

        // and then - both key string length and key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("a")},
            new NottableString[]{string("someone@mockserver.com"), string("abcdef")}
        )), is(false));

        // NOT-ED

        // then - does match (not-ed both)
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("abc"), not("abc")},
            new NottableString[]{not("someone@mockserver.com"), not("someone@mockserver.com")}
        )), is(true));

        // then - doesn't match (not-ed keys)
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("abc"), string("abc")},
            new NottableString[]{not("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(false));

        // then - doesn't match (not-ed values)
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("abc")},
            new NottableString[]{string("someone@mockserver.com"), not("someone@mockserver.com")}
        )), is(false));

        // key and value don't match

        // and then - both string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), not("a")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(true));

        // and then - both emails don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{not("abcdef"), not("abcdef")}
        )), is(true));

        // and then - both string lengths and emails don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), not("a")},
            new NottableString[]{not("abcdef"), not("abcdef")}
        )), is(true));

        // key don't match

        // and then - key string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(true));

        // and then - key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{not("abcdef"), string("someone@mockserver.com")}
        )), is(true));

        // and then - both key string length and key email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{not("a"), string("abc")},
            new NottableString[]{not("abcdef"), string("someone@mockserver.com")}
        )), is(true));


        // value don't match

        // and then - value string lengths don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("a")},
            new NottableString[]{string("someone@mockserver.com"), string("someone@mockserver.com")}
        )), is(true));

        // and then - value email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), not("abcdef")}
        )), is(true));

        // and then - both value string length and value email don't match
        assertThat(multiMap.containsAll(multiMap(
            true,
            KeyMatchStyle.SUB_SET,
            new NottableString[]{string("abc"), not("a")},
            new NottableString[]{string("someone@mockserver.com"), not("abcdef")}
        )), is(true));
    }
}
