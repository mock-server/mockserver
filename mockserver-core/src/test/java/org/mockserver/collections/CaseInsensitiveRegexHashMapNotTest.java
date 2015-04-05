package org.mockserver.collections;

import com.google.common.collect.Sets;
import org.junit.Test;
import org.mockserver.model.NottableString;

import java.util.Arrays;

import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.Assert.*;
import static org.mockserver.model.NottableString.not;
import static org.mockserver.model.NottableString.string;
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
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("key")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("key End")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("Beginning key")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("Beginning key End")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("KEY")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("KEY End")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("Beginning KEY")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("Beginning KEY End")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(string("AnotherValue")));
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
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("key")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("key End")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("Beginning key")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("Beginning key End")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("KEY")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("KEY End")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("Beginning KEY")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("Beginning KEY End")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(not("AnotherValue")));
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
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("key")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("KEY")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("Key")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("kEy")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("keY")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("kEY")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("OTHERKEY")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(string("otherkey")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(string("notAKey")));
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
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("key")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("KEY")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("Key")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("kEy")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("keY")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("kEY")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("OTHERKEY")));
        assertFalse(caseInsensitiveRegexHashMap.containsKey(not("otherkey")));
        assertTrue(caseInsensitiveRegexHashMap.containsKey(not("notAKey")));
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
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(string("key")));
        assertEquals("valueTwo", caseInsensitiveRegexHashMap.get(string("key End")));
        assertEquals("valueThree", caseInsensitiveRegexHashMap.get(string("Beginning key")));
        assertEquals("valueFour", caseInsensitiveRegexHashMap.get(string("Beginning key End")));
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
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(not("does_not_exist")));
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
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(string("key")));
        assertEquals("valueTwo", caseInsensitiveRegexHashMap.get(string("KEY")));
        assertEquals("valueThree", caseInsensitiveRegexHashMap.get(string("Key")));
        assertEquals("valueFour", caseInsensitiveRegexHashMap.get(string("OtherKey")));
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
        assertEquals("valueOne", caseInsensitiveRegexHashMap.get(not("does_not_exist")));
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
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree", "valueFour"), caseInsensitiveRegexHashMap.getAll(string("key")));
        assertSameEntries(Arrays.asList("valueTwo", "valueFour"), caseInsensitiveRegexHashMap.getAll(string("key End")));
        assertSameEntries(Arrays.asList("valueThree", "valueFour"), caseInsensitiveRegexHashMap.getAll(string("Beginning key")));
        assertEquals(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(string("Beginning key End")));
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
        assertThat(caseInsensitiveRegexHashMap.getAll(not("key")), empty());
        assertThat(caseInsensitiveRegexHashMap.getAll(not("key End")), contains("valueOne", "valueThree"));
        assertThat(caseInsensitiveRegexHashMap.getAll(not("Beginning key")), contains("valueOne", "valueTwo"));
        assertThat(caseInsensitiveRegexHashMap.getAll(not("Beginning key End")), contains("valueOne", "valueTwo", "valueThree"));
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
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(string("key")));
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(string("KEY")));
        assertSameEntries(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(string("Key")));
        assertEquals(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(string("OtherKey")));
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
        assertSameEntries(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(not("key")));
        assertSameEntries(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(not("KEY")));
        assertSameEntries(Arrays.asList("valueFour"), caseInsensitiveRegexHashMap.getAll(not("Key")));
        assertEquals(Arrays.asList("valueOne", "valueTwo", "valueThree"), caseInsensitiveRegexHashMap.getAll(not("OtherKey")));
    }

    @Test
    public void shouldSupportRemoving() {
        // given
        CaseInsensitiveRegexHashMap<String> circularMultiMap = new CaseInsensitiveRegexHashMap<String>();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove(string("one")));
        assertNull(circularMultiMap.remove(string("one")));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet(string("two")), circularMultiMap.keySet());
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
        assertEquals("two", circularMultiMap.remove(not("one")));
        assertNull(circularMultiMap.remove(not("one")));

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
        assertEquals("one_one", circularMultiMap.remove(string("o[a-z]{2}")));
        assertNull(circularMultiMap.remove(string("one")));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet(string("two")), circularMultiMap.keySet());
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
        assertEquals("two", circularMultiMap.remove(not("o[a-z]{2}")));
        assertNull(circularMultiMap.remove(not("one")));

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
        assertEquals("one_one", circularMultiMap.remove(string("ONE")));
        assertEquals("two", circularMultiMap.remove(string("Two")));

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
        assertEquals("two", circularMultiMap.remove(not("ONE")));
        assertEquals("one_one", circularMultiMap.remove(not("Two")));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
    }
}
