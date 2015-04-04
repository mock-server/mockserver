package org.mockserver.collections;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.*;
import static org.mockserver.collections.NottableKey.nottableKey;
import static org.mockserver.test.Assert.assertSameEntries;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexHashMapNotTest {

    @Test
    public void shouldFindKeyUsingRegex() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("key", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("key End", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning key", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning key End", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("KEY", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("KEY End", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning KEY", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning KEY End", false)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("AnotherValue", false)));
    }

    @Test
    public void shouldNotFindKeyUsingRegexInNottedKey() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("key", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("key End", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning key", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning key End", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("KEY", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("KEY End", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning KEY", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("Beginning KEY End", true)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("AnotherValue", true)));
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
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("key", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("KEY", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("Key", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("kEy", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("keY", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("kEY", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("OTHERKEY", false)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("otherkey", false)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("notAKey", false)));
    }

    @Test
    public void shouldNotFindKeyIgnoringCaseInNottedKey() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("KEY", "valueTwo");
        caseInsensitiveRegexHashMap.put("Key", "valueThree");
        caseInsensitiveRegexHashMap.put("OtherKey", "valueFour");

        // then
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("key", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("KEY", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("Key", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("kEy", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("keY", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("kEY", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("OTHERKEY", true)));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(nottableKey("otherkey", true)));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(nottableKey("notAKey", true)));
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
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(nottableKey("key", false)));
        assertEquals("valueTwo", caseInsensitiveRegexHashMap.get(nottableKey("key End", false)));
        assertEquals("valueThree", caseInsensitiveRegexHashMap.get(nottableKey("Beginning key", false)));
        assertEquals("valueFour", caseInsensitiveRegexHashMap.get(nottableKey("Beginning key End", false)));
    }

    @Test
    public void shouldNotGetValueUsingRegex() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(nottableKey("does_not_exist", true)));
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
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(nottableKey("key", false)));
        assertEquals("valueTwo", caseInsensitiveRegexHashMap.get(nottableKey("KEY", false)));
        assertEquals("valueThree", caseInsensitiveRegexHashMap.get(nottableKey("Key", false)));
        assertEquals("valueFour", caseInsensitiveRegexHashMap.get(nottableKey("OtherKey", false)));
    }

    @Test
    public void shouldNotGetValueIgnoringCase() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("KEY", "valueTwo");
        caseInsensitiveRegexHashMap.put("Key", "valueThree");
        caseInsensitiveRegexHashMap.put("OtherKey", "valueFour");

        // then
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(nottableKey("does_not_exist", true)));
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
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree", "valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("key", false)));
        assertSameEntries(Arrays.asList("valueTwo", "valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("key End", false)));
        assertSameEntries(Arrays.asList("valueThree", "valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("Beginning key", false)));
        assertEquals(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("Beginning key End", false)));
    }

    @Test
    public void shouldNotGetAllValuesUsingRegex() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("key.*", "valueTwo");
        caseInsensitiveRegexHashMap.put(".*key", "valueThree");
        caseInsensitiveRegexHashMap.put(".*key.*", "valueFour");

        // then
        assertThat(caseInsensitiveRegexHashMap.getAll(nottableKey("key", true)), empty());
        assertThat(caseInsensitiveRegexHashMap.getAll(nottableKey("key End", true)), contains("valueOne", "valueThree"));
        assertThat(caseInsensitiveRegexHashMap.getAll(nottableKey("Beginning key", true)), contains("valueOne", "valueTwo"));
        assertThat(caseInsensitiveRegexHashMap.getAll(nottableKey("Beginning key End", true)), contains("valueOne", "valueTwo", "valueThree"));
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
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(nottableKey("key", false)));
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(nottableKey("KEY", false)));
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(nottableKey("Key", false)));
        assertEquals(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("OtherKey", false)));
    }

    @Test
    public void shouldNotGetAllValuesIgnoringCase() {
        // when
        CaseInsensitiveRegexHashMap<String> caseInsensitiveRegexHashMap = new CaseInsensitiveRegexHashMap<String>();
        caseInsensitiveRegexHashMap.put("key", "valueOne");
        caseInsensitiveRegexHashMap.put("KEY", "valueTwo");
        caseInsensitiveRegexHashMap.put("Key", "valueThree");
        caseInsensitiveRegexHashMap.put("OtherKey", "valueFour");

        // then
        assertSameEntries(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("key", true)));
        assertSameEntries(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("KEY", true)));
        assertSameEntries(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(nottableKey("Key", true)));
        assertEquals(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(nottableKey("OtherKey", true)));
    }

    @Test
    public void shouldSupportRemoving() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove(nottableKey("one", false)));
        assertNull(circularMultiMap.remove(nottableKey("one", false)));

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
    public void shouldSupportNotRemoving() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("two", circularMultiMap.remove(nottableKey("one", true)));
        assertNull(circularMultiMap.remove(nottableKey("one", true)));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("two"));
        assertTrue(circularMultiMap.containsKey("one"));
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("two"));
        assertTrue(circularMultiMap.containsValue("one_one"));
    }

    @Test
    public void shouldSupportRemovingWithRegex() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove(nottableKey("o[a-z]{2}", false)));
        assertNull(circularMultiMap.remove(nottableKey("one", false)));

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
    public void shouldSupportNotRemovingWithRegex() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("two", circularMultiMap.remove(nottableKey("o[a-z]{2}", true)));
        assertNull(circularMultiMap.remove(nottableKey("one", true)));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("two"));
        assertTrue(circularMultiMap.containsKey("one"));
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("two"));
        assertTrue(circularMultiMap.containsValue("one_one"));
    }

    @Test
    public void shouldSupportRemovingWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove(nottableKey("ONE", false)));
        assertEquals("two", circularMultiMap.remove(nottableKey("Two", false)));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
    }

    @Test
    public void shouldSupportNotRemovingWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("two", circularMultiMap.remove(nottableKey("ONE", true)));
        assertEquals("one_one", circularMultiMap.remove(nottableKey("Two", true)));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
    }
}
