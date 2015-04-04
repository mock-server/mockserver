package org.mockserver.collections;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;
import static org.mockserver.collections.NottableKey.nottableKey;
import static org.mockserver.test.Assert.assertSameEntries;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapTest {

    @Test
    public void shouldFindKeyUsingRegex() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertTrue(caseInsensitiveRegexHashMap.containsKey("key"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("key End"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("Beginning key"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("Beginning key End"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("KEY"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("KEY End"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("Beginning KEY"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("Beginning KEY End"));
        assertFalse(caseInsensitiveRegexHashMap.containsKey("AnotherValue"));
    }

    @Test
    public void shouldFindKeyIgnoringCase() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("KEY", "valueTwo");
        caseInsensitiveRegexHashMap.put("Key", "valueThree");
        caseInsensitiveRegexHashMap.put("OtherKey", "valueFour");

        // then
        assertTrue(caseInsensitiveRegexHashMap.containsKey("key"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("KEY"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("Key"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("kEy"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("keY"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("kEY"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("OTHERKEY"));
        assertTrue(caseInsensitiveRegexHashMap.containsKey("otherkey"));
        assertFalse(caseInsensitiveRegexHashMap.containsKey("notAKey"));
    }

    @Test
    public void shouldGetValueUsingRegex() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get("key"));
        assertEquals("valueTwo", caseInsensitiveRegexHashMap.get("key End"));
        assertEquals("valueThree", caseInsensitiveRegexHashMap.get("Beginning key"));
        assertEquals("valueFour", caseInsensitiveRegexHashMap.get("Beginning key End"));
    }

    @Test
    public void shouldGetValueIgnoringCase() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("KEY", "valueTwo");
        caseInsensitiveRegexHashMap.put("Key", "valueThree");
        caseInsensitiveRegexHashMap.put("OtherKey", "valueFour");

        // then
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get("key"));
        assertEquals("valueTwo", caseInsensitiveRegexHashMap.get("KEY"));
        assertEquals("valueThree", caseInsensitiveRegexHashMap.get("Key"));
        assertEquals("valueFour", caseInsensitiveRegexHashMap.get("OtherKey"));
    }

    @Test
    public void shouldGetAllValuesUsingRegex() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree", "valueFour"), caseInsensitiveRegexHashMap.getAll("key"));
        assertSameEntries(Arrays.asList("valueTwo", "valueFour"), caseInsensitiveRegexHashMap.getAll("key End"));
        assertSameEntries(Arrays.asList("valueThree", "valueFour"), caseInsensitiveRegexHashMap.getAll("Beginning key"));
        assertEquals(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll("Beginning key End"));
    }

    @Test
    public void shouldGetAllValuesIgnoringCase() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("KEY", "valueTwo");
        caseInsensitiveRegexHashMap.put("Key", "valueThree");
        caseInsensitiveRegexHashMap.put("OtherKey", "valueFour");

        // then
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll("key"));
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll("KEY"));
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll("Key"));
        assertEquals(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll("OtherKey"));
    }

    @Test
    public void shouldSupportRemoving() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove("one"));
        assertNull(circularMultiMap.remove("one"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet(nottableKey("two")), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("two"));
    }

    @Test
    public void shouldSupportRemovingWithRegex() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove("o[a-z]{2}"));
        assertNull(circularMultiMap.remove("one"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet(nottableKey("two")), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("two"));
    }

    @Test
    public void shouldSupportRemovingWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove("ONE"));
        assertEquals("two", circularMultiMap.remove("Two"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
    }
}
