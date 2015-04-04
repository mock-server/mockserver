package org.mockserver.collections;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.hamcrest.collection.IsEmptyCollection.empty;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockserver.collections.NottableKey.nottableKey;

/**
 * @author jamesdbloom
 */
public class CaseInsensitiveRegexMultiMapNotTest {

    @Test
    public void shouldSupportRemovingAllValues() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll(nottableKey("one", false)));
        assertNull(circularMultiMap.removeAll(nottableKey("three", false)));

        // then
        assertEquals(1, circularMultiMap.size());
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "two"));
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
    public void shouldSupportNotRemovingAllValues() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("two"), circularMultiMap.removeAll(nottableKey("one", true)));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll(nottableKey("three", true)));

        // then
        assertEquals(0, circularMultiMap.size());
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), empty());
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
        assertEquals("one_one", circularMultiMap.remove(nottableKey("one", false)));
        assertNull(circularMultiMap.remove(nottableKey("three", false)));

        // then
        assertEquals(2, circularMultiMap.size());
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "one", "two"));
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
    public void shouldSupportNotRemovingAValue() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("two", circularMultiMap.remove(nottableKey("one", true)));
        assertEquals("one_one", circularMultiMap.remove(nottableKey("three", true)));

        // then
        assertEquals(2, circularMultiMap.size());
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "one", "two"));
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("one_two"));
        assertTrue(circularMultiMap.containsValue("one_three"));
        assertFalse(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("one_two", "one_three"), circularMultiMap.values());
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
        assertEquals("one_one", circularMultiMap.remove(nottableKey("o[a-z]{2}", false)));
        assertNull(circularMultiMap.remove(nottableKey("t[a-z]{3}", false)));

        // then
        assertEquals(2, circularMultiMap.size());
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("o.*"));
        assertTrue(circularMultiMap.containsKey("T[a-z]{2}"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "one", "two"));
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
    public void shouldSupportNotRemovingAValueWithRegex() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("two", circularMultiMap.remove(nottableKey("o[a-z]{2}", true)));
        assertEquals("one_one", circularMultiMap.remove(nottableKey("t[a-z]{3}", true)));

        // then
        assertEquals(2, circularMultiMap.size());
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "one", "two"));
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("one_two"));
        assertTrue(circularMultiMap.containsValue("one_three"));
        assertFalse(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("one_two", "one_three"), circularMultiMap.values());
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
        assertEquals("one_one", circularMultiMap.remove(nottableKey("ONE", false)));
        assertNull(circularMultiMap.remove(nottableKey("three", false)));

        // then
        assertEquals(2, circularMultiMap.size());
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("oNE"));
        assertTrue(circularMultiMap.containsKey("Two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "one", "two"));
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
    public void shouldSupportNotRemovingAValueWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals("two", circularMultiMap.remove(nottableKey("ONE", true)));
        assertEquals("one_one", circularMultiMap.remove(nottableKey("three", true)));

        // then
        assertEquals(2, circularMultiMap.size());
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "one", "two"));
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("one_one"));
        assertTrue(circularMultiMap.containsValue("one_two"));
        assertTrue(circularMultiMap.containsValue("one_three"));
        assertFalse(circularMultiMap.containsValue("two"));
        assertEquals(Arrays.asList("one_two", "one_three"), circularMultiMap.values());
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
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll(nottableKey("o[a-z]{2}", false)));
        assertNull(circularMultiMap.removeAll(nottableKey("t[a-z]{3}", false)));

        // then
        assertEquals(1, circularMultiMap.size());
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "two"));
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
    public void shouldSupportNotRemovingAllValuesWithRegex() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("two"), circularMultiMap.removeAll(nottableKey("o[a-z]{2}", true)));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll(nottableKey("t[a-z]{3}", true)));

        // then
        assertEquals(0, circularMultiMap.size());
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), empty());
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
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll(nottableKey("ONE", false)));
        assertNull(circularMultiMap.removeAll(nottableKey("three", false)));

        // then
        assertEquals(1, circularMultiMap.size());
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertTrue(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), containsInAnyOrder((Object) "two"));
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
    public void shouldSupportNotRemovingAllValuesWithCaseInsensitivity() {
        // given
        CaseInsensitiveRegexMultiMap circularMultiMap = new CaseInsensitiveRegexMultiMap();
        circularMultiMap.put("one", "one_one");
        circularMultiMap.put("one", "one_two");
        circularMultiMap.put("one", "one_three");
        circularMultiMap.put("two", "two");

        // when
        assertEquals(Arrays.asList("two"), circularMultiMap.removeAll(nottableKey("ONE", true)));
        assertEquals(Arrays.asList("one_one", "one_two", "one_three"), circularMultiMap.removeAll(nottableKey("three", true)));

        // then
        assertEquals(0, circularMultiMap.size());
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("one"));
        assertFalse(circularMultiMap.containsKey("two"));
        assertThat(circularMultiMap.keySet(), empty());
    }
}
