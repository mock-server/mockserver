package org.mockserver.collections.hashmap.schemamatched;

import org.junit.Test;
import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.model.NottableString;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.collections.NottableStringHashMap.hashMap;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTestSchemaContainsAll {
    String stringLengthSchema = "{" + NEW_LINE +
        "  \"type\": \"string\"," + NEW_LINE +
        "  \"minLength\": 2," + NEW_LINE +
        "  \"maxLength\": 3" + NEW_LINE +
        "}";

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")}
        );

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), string("keyOne_valueOne")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("a"), string("keyOne_valueOne")}
        ).containsAll(hashMap), is(false));

        // NOT-ED

        // then
        assertThat(hashMap(
            true, new NottableString[]{not("abc"), string("keyOne_valueOne")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{not("a"), string("keyOne_valueOne")}
        ).containsAll(hashMap), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), string("abc")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), string("a")}
        ).containsAll(hashMap), is(false));

        // NOT-ED

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), not("abc")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), not("a")}
        ).containsAll(hashMap), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), string("abc")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("a"), string("a")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), string("a")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("a"), string("abc")}
        ).containsAll(hashMap), is(false));

        // NOT-ED

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), not("abc")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{not("a"), not("a")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), not("a")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{not("a"), string("abc")}
        ).containsAll(hashMap), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")}
        );

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("a"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // NOT-ED

        // then
        assertThat(hashMap(
            true, new NottableString[]{not("abc"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{not("a"), string("keyOne_valueOne")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), string("a")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // NOT-ED

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), not("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("keyOne"), not("a")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("a"), string("a")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), string("a")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("a"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // NOT-ED

        // then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), not("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(false));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{not("a"), not("a")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{string("abc"), not("a")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));

        // and then
        assertThat(hashMap(
            true, new NottableString[]{not("a"), string("abc")},
            new NottableString[]{string("someone@mockserver.com"), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        ).containsAll(hashMap), is(true));
    }

}
