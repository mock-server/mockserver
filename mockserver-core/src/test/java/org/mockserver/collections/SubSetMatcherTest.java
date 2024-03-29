package org.mockserver.collections;

import org.junit.Test;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.matchers.RegexStringMatcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockserver.collections.SubSetMatcher.containsSubset;
import static org.mockserver.model.NottableSchemaString.schemaString;
import static org.mockserver.model.NottableString.string;

public class SubSetMatcherTest {

    static RegexStringMatcher regexStringMatcher = new RegexStringMatcher(new MockServerLogger(), false);

    @Test
    public void shouldContainSubsetForEmpty() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Collections.emptyList(),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForMultiValuesAllMatching() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForMultiValuesSubset() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForNottedKey() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "!two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "notTwo", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForNottedValue() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "!two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "notTwo"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForNottedMultiValue() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "!one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "notOne_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForSubsetWithPresentOptional() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForSubsetWithNotPresentOptional() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForOptionalWrongValue() {
        assertFalse(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "wrong")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForOptionalWrongValueInReverse() {
        assertFalse(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "?four", "wrong")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForExtraValue() {
        assertFalse(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four"),
                new ImmutableEntry(regexStringMatcher, "five", "five")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForWrongValue() {
        assertFalse(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "wrong"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForWrongKey() {
        assertFalse(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "wrong", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three"),
                new ImmutableEntry(regexStringMatcher, "four", "four")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForSchemaKey() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, schemaString("{\"type\": \"string\", \"pattern\": \"tw.{1}\"}"), string("two"))
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForSchemaValue() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, string("two"), schemaString("{\"type\": \"string\", \"pattern\": \"tw.{1}\"}"))
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForSchemaMultiValue() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, string("one"), schemaString("{\"type\": \"string\", \"pattern\": \"one_t.*\"}")),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

    @Test
    public void shouldContainSubsetForSchemaKeyAndMultiValue() {
        assertTrue(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, schemaString("{\"type\": \"string\", \"pattern\": \"o.*\"}"), string("one_one")),
                new ImmutableEntry(regexStringMatcher, schemaString("{\"type\": \"string\", \"pattern\": \"o.*\"}"), string("one_two")),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "one", "one_two"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
        assertFalse(containsSubset(null, null, regexStringMatcher,
            Arrays.asList(
                new ImmutableEntry(regexStringMatcher, schemaString("{\"type\": \"string\", \"pattern\": \"o.*\"}"), string("one_one")),
                new ImmutableEntry(regexStringMatcher, schemaString("{\"type\": \"string\", \"pattern\": \"o.*\"}"), string("one_two")),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "?four", "four")
            ),
            new ArrayList<>(Arrays.asList(
                new ImmutableEntry(regexStringMatcher, "one", "one_one"),
                new ImmutableEntry(regexStringMatcher, "two", "two"),
                new ImmutableEntry(regexStringMatcher, "three", "three")
            )))
        );
    }

}