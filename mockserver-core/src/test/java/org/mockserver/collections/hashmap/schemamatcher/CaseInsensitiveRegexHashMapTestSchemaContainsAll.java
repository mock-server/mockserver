package org.mockserver.collections.hashmap.schemamatcher;

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
    String emailPatternSchema = "{" + NEW_LINE +
        "    \"type\": \"string\"," + NEW_LINE +
        "    \"format\": \"email\"" + NEW_LINE +
        "}";

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), string("keyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("a"), string("keyOne_valueOne")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("abc"), string("keyOne_valueOne")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("a"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("abc")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("a")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("abc")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("a")}
        )), is(true));
    }

    @Test
    public void shouldContainAllExactMatchSingleKeyAndSingleValueForSchemaKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), string("abc")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("a"), string("a")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), string("a")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("a"), string("abc")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), not("abc")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("a"), not("a")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), not("a")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("a"), string("abc")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaKey() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), string("keyOne_valueOne")},
            new NottableString[]{schemaString(emailPatternSchema), string("keyTwo_valueOne"), string("keyTwo_valueTwo")}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), string("keyOne_valueOne")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("a"), string("keyOne_valueOne")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("abc"), string("keyOne_valueOne")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("a"), string("keyOne_valueOne")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{string("keyOne"), schemaString(stringLengthSchema)},
            new NottableString[]{string("keyTwo"), schemaString(emailPatternSchema)}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("abc")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), string("a")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("abc")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("keyOne"), not("a")}
        )), is(true));
    }

    @Test
    public void shouldContainAllSubSetSingleKeyAndSingleValueForSchemaKeyAndValue() {
        // given
        NottableStringHashMap hashMap = hashMap(
            true, new NottableString[]{schemaString(stringLengthSchema), schemaString(stringLengthSchema)},
            new NottableString[]{schemaString(emailPatternSchema), schemaString(emailPatternSchema)}
        );

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), string("abc")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("a"), string("a")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), string("a")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("a"), string("abc")}
        )), is(false));

        // NOT-ED

        // then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), not("abc")}
        )), is(false));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("a"), not("a")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{string("abc"), not("a")}
        )), is(true));

        // and then
        assertThat(hashMap.containsAll(hashMap(
            true, new NottableString[]{not("a"), string("abc")}
        )), is(true));
    }

}
