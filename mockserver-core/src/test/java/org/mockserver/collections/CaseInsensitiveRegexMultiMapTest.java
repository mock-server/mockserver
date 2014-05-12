package org.mockserver.collections;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapTest {

    @Test
    public void shouldStoreMultipleValuesAgainstSingleKey() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();

        // when
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // then
        assertEquals(Arrays.asList("one_one", "one_two", "one_three", "two"), circularMultiMap.getAll("[a-z]{3}"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("o[a-z]{2}"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("ONE"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("One"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("oNE"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("TWO"));
    }

    @Test
    public void shouldSupportPuttingAllEntriesInAMap() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();

        // when
        circularMultiMap.put("one", "one_one");
        circularMultiMap.putAll(new HashMap<String, String>() {
            private static final long serialVersionUID = -580164440676146851L;

            {
                put("one", "one_two");
                put("two", "two");
            }
        });
        circularMultiMap.put("one", "one_three");

        // then
        assertEquals(Arrays.asList("one_one", "one_two", "one_three", "two"), circularMultiMap.getAll("[a-z]{3}"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("o[a-z]{2}"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("ONE"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("One"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("oNE"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("TWO"));
    }

    @Test
    public void shouldSupportPuttingValuesForNewKeysOnly() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();

        // when
        circularMultiMap.put("one", "one_one");
        circularMultiMap.putValuesForNewKeys(new CaseInsensitiveRegexMultiMap() {{
            put("one", "one_two");
            put("two", "two");
        }});
        circularMultiMap.put("one", "one_three");

        // then
        assertEquals(Arrays.asList("one_one", "one_three", "two"), circularMultiMap.getAll("[a-z]{3}"));
        assertEquals(Arrays.asList("one_one", "one_three"), circularMultiMap.getAll("o[a-z]{2}"));
        assertEquals(Arrays.asList("one_one", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("one_one", "one_three"), circularMultiMap.getAll("ONE"));
        assertEquals(Arrays.asList("one_one", "one_three"), circularMultiMap.getAll("One"));
        assertEquals(Arrays.asList("one_one", "one_three"), circularMultiMap.getAll("oNE"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("TWO"));
    }

    @Test
    public void shouldSupportPuttingMultipleValuesInAList() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();

        // when
        circularMultiMap.put("one", Arrays.asList("one_one"));
        circularMultiMap.put("one", Arrays.asList("one_two", "one_three"));

        // then
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("[a-z]{3}"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("o[a-z]{2}"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("ONE"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("One"));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.getAll("oNE"));
    }

    @Test

    public void shouldIndicateWhenEmpty() {
        assertTrue(new CaseInsensitiveRegexMultiMap().isEmpty());
    }

    @Test

    public void shouldSupportBeingCleared() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        circularMultiMap.clear();

        // then
        assertTrue(circularMultiMap.isEmpty());
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
        assertFalse(circularMultiMap.containsValue("one_two"));
        assertFalse(circularMultiMap.containsValue("two"));
    }

    @Test

    public void shouldReturnEntrySet() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();

        // when
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // then
        assertEquals(Sets.newHashSet(
                new CaseInsensitiveRegexMultiMap.ImmutableEntry[]{
                        circularMultiMap.new ImmutableEntry("one", "one_one"),
                        circularMultiMap.new ImmutableEntry("one", "one_two"),
                        circularMultiMap.new ImmutableEntry("one", "one_three"),
                        circularMultiMap.new ImmutableEntry("two", "two")
                }
        ), circularMultiMap.entrySet());
    }

    @Test

    public void shouldCorrectlyConstructAndGetEntryValue() {
        // when
        CaseInsensitiveRegexMultiMap.ImmutableEntry immutableEntry = new CaseInsensitiveRegexMultiMap().new ImmutableEntry("key", "value");

        // then
        assertEquals(immutableEntry.getKey(), "key");
        assertEquals(immutableEntry.getValue(), "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldNotAllowImmutableEntryToBeModified() {
        new CaseInsensitiveRegexMultiMap().new ImmutableEntry("key", "value").setValue("new_value");
    }

    @Test
    public void shouldSupportRemovingAllValues() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll("one"));
        assertNull(circularMultiMap.removeAll("three"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet("two"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertFalse(circularMultiMap.containsValue("one_two"));
        assertFalse(circularMultiMap.containsValue("one_three"));
        assertTrue(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("two"), circularMultiMap.values());
        // - should have correct values per key
        assertNull(circularMultiMap.get("one"));
        assertEquals("two", circularMultiMap.get("two"));
        assertEquals(0, circularMultiMap.getAll("one").size());
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
    }

    @Test
    public void shouldSupportRemovingAValue() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove("one"));
        assertNull(circularMultiMap.remove("three"));

        // then
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet("one", "two"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("one_two"));
        assertTrue(circularMultiMap.containsValue("one_three"));
        assertTrue(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("one_two", "one_three", "two"), circularMultiMap.values());
        // - should have correct values per key
        assertEquals("one_two", circularMultiMap.get("one"));
        assertEquals("two", circularMultiMap.get("two"));
        assertEquals(Arrays.asList("one_two", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
    }

    @Test
    public void shouldSupportRemovingAValueWithRegex() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove("o[a-z]{2}"));
        assertNull(circularMultiMap.remove("t[a-z]{3}"));

        // then
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("o.*"));
        assertTrue(circularMultiMap.containsKey("T[a-z]{2}"));
        assertEquals(Sets.newHashSet("one", "two"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("one_two"));
        assertTrue(circularMultiMap.containsValue("one_three"));
        assertTrue(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("one_two", "one_three", "two"), circularMultiMap.values());
        // - should have correct values per key
        assertEquals("one_two", circularMultiMap.get(".*n.*"));
        assertEquals("two", circularMultiMap.get(".*o"));
        assertEquals(Arrays.asList("one_two", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
    }

    @Test
    public void shouldSupportRemovingAllValuesWithRegex() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll("o[a-z]{2}"));
        assertNull(circularMultiMap.removeAll("t[a-z]{3}"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet("two"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertFalse(circularMultiMap.containsValue("one_two"));
        assertFalse(circularMultiMap.containsValue("one_three"));
        assertTrue(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("two"), circularMultiMap.values());
        // - should have correct values per key
        assertNull(circularMultiMap.get("one"));
        assertEquals("two", circularMultiMap.get("two"));
        assertEquals(0, circularMultiMap.getAll("one").size());
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
    }

    @Test
    public void shouldSupportRemovingAValueWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("one_one", circularMultiMap.remove("ONE"));
        assertNull(circularMultiMap.remove("three"));

        // then
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("oNE"));
        assertTrue(circularMultiMap.containsKey("Two"));
        assertEquals(Sets.newHashSet("one", "two"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("one_two"));
        assertTrue(circularMultiMap.containsValue("one_three"));
        assertTrue(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("one_two", "one_three", "two"), circularMultiMap.values());
        // - should have correct values per key
        assertEquals("one_two", circularMultiMap.get("oNe"));
        assertEquals("two", circularMultiMap.get("twO"));
        assertEquals(Arrays.asList("one_two", "one_three"), circularMultiMap.getAll("one"));
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
    }

    @Test
    public void shouldSupportRemovingAllValuesWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll("ONE"));
        assertNull(circularMultiMap.removeAll("three"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertEquals(Sets.newHashSet("two"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertFalse(circularMultiMap.containsValue("one_two"));
        assertFalse(circularMultiMap.containsValue("one_three"));
        assertTrue(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("two"), circularMultiMap.values());
        // - should have correct values per key
        assertNull(circularMultiMap.get("one"));
        assertEquals("two", circularMultiMap.get("two"));
        assertEquals(0, circularMultiMap.getAll("one").size());
        assertEquals(Arrays.asList("two"), circularMultiMap.getAll("two"));
    }
}
