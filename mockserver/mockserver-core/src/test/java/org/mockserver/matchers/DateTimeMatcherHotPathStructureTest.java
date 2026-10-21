package org.mockserver.matchers;

import org.junit.Test;
import org.mockserver.collections.NottableStringHashMap;
import org.mockserver.collections.NottableStringMultiMap;
import org.mockserver.collections.SubSetMatcher;
import org.mockserver.model.NottableString;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Pins the structure that keeps date-time matchers free for expectations that do not use them: a plain
 * value never reaches date-time code, because a date-time matcher is a {@link NottableString} subtype
 * dispatched by the {@code instanceof} that already existed for JSON schema values, and no field was
 * added to every {@link NottableString}.
 */
public class DateTimeMatcherHotPathStructureTest {

    @Test
    public void shouldNotReferenceDateTimeCodeFromTheKeyValueMatchingPath() throws IOException {
        for (Class<?> hotPathClass : new Class<?>[]{
            RegexStringMatcher.class,
            SubSetMatcher.class,
            NottableStringMultiMap.class,
            NottableStringHashMap.class,
            MultiValueMapMatcher.class,
            HashMapMatcher.class,
            BinaryHeaderValueNormalizer.class,
            NottableString.class
        }) {
            assertThat(hotPathClass.getSimpleName(), constantPoolText(hotPathClass), not(containsString("DateTime")));
        }
    }

    @Test
    public void shouldDispatchMatcherValuesThroughTheExistingMatcherStringCheck() throws IOException {
        String regexStringMatcher = constantPoolText(RegexStringMatcher.class);
        assertThat(regexStringMatcher, containsString("org/mockserver/model/NottableMatcherString"));
        assertThat(regexStringMatcher, not(containsString("org/mockserver/model/NottableSchemaString")));
    }

    @Test
    public void shouldNotAddAnyFieldToEveryNottableString() {
        Set<String> instanceFields = Arrays.stream(NottableString.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(Field::getName)
            .collect(Collectors.toCollection(TreeSet::new));
        assertThat(instanceFields.toString(), is("[caseSensitivePattern, hashCode, isBlank, not, pattern, styleAndSchema, value]"));
    }

    private static String constantPoolText(Class<?> type) throws IOException {
        try (InputStream classFile = type.getResourceAsStream(type.getSimpleName() + ".class")) {
            return new String(classFile.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }
}
