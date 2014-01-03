package org.mockserver.collections;

import com.google.common.collect.Sets;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class CircularMultiMapTest {

    @Test
    public void shouldStoreMultipleValuesAgainstSingleKey() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);

        // when
        circularMultiMap.put("1", "1_1");
        circularMultiMap.put("1", "1_2");
        circularMultiMap.put("1", "1_3");
        circularMultiMap.put("2", "2");

        // then
        assertEquals(Arrays.asList("1_1", "1_2", "1_3"), circularMultiMap.getAll("1"));
        assertEquals(Arrays.asList("2"), circularMultiMap.getAll("2"));
    }

    @Test
    public void shouldNotContainMoreThenMaximumNumberOfKeys() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);

        // when
        circularMultiMap.put("1", "1");
        circularMultiMap.put("2", "2");
        circularMultiMap.put("3", "3");
        circularMultiMap.put("4", "4");

        // then
        assertEquals(3, circularMultiMap.size());
        assertFalse(circularMultiMap.containsKey("1"));
        assertTrue(circularMultiMap.containsKey("2"));
        assertTrue(circularMultiMap.containsKey("3"));
        assertTrue(circularMultiMap.containsKey("4"));
        assertEquals(Sets.newHashSet("2", "3", "4"), circularMultiMap.keySet());
    }

    @Test
    public void shouldNotAllowAddingMoreThenMaximumNumberOfValuePerKey() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);

        // when
        circularMultiMap.put("1", "1");
        circularMultiMap.put("1", "2");
        circularMultiMap.put("1", "3");
        circularMultiMap.put("1", "4");
        circularMultiMap.put("1", "5");
        circularMultiMap.put("2", "2");
        circularMultiMap.put("2", "3");

        // then
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("1"));
        assertTrue(circularMultiMap.containsKey("2"));
        assertEquals(Sets.newHashSet("1", "2"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("1"));
        assertTrue(circularMultiMap.containsValue("2"));
        assertTrue(circularMultiMap.containsValue("3"));
        assertTrue(circularMultiMap.containsValue("4"));
        assertTrue(circularMultiMap.containsValue("5"));
        assertEquals(Arrays.asList("3", "4", "5", "2", "3"), circularMultiMap.values());
        // - should have correct values per key
        assertEquals("3", circularMultiMap.get("1"));
        assertEquals("2", circularMultiMap.get("2"));
        assertEquals(Arrays.asList("3", "4", "5"), circularMultiMap.getAll("1"));
        assertEquals(Arrays.asList("2", "3"), circularMultiMap.getAll("2"));
    }

    @Test
    public void shouldSupportPuttingAllEntriesInAMap() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);

        // when
        circularMultiMap.put("1", "1_1");
        circularMultiMap.putAll(new HashMap<String, String>() {
            private static final long serialVersionUID = -580164440676146851L;

            {
                put("1", "1_2");
                put("2", "2");
            }
        });
        circularMultiMap.put("1", "1_3");

        // then
        assertEquals(Arrays.asList("1_1", "1_2", "1_3"), circularMultiMap.getAll("1"));
        assertEquals(Arrays.asList("2"), circularMultiMap.getAll("2"));
    }

    @Test

    public void shouldIndicateWhenEmpty() {
        assertTrue(new CircularMultiMap<>(3, 3).isEmpty());
    }

    @Test

    public void shouldSupportBeingCleared() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);
        circularMultiMap.put("1", "1_1");
        circularMultiMap.put("1", "1_2");
        circularMultiMap.put("1", "1_3");
        circularMultiMap.put("2", "2");

        // when
        circularMultiMap.clear();

        // then
        assertTrue(circularMultiMap.isEmpty());
        assertFalse(circularMultiMap.containsKey("1"));
        assertFalse(circularMultiMap.containsKey("2"));
        assertFalse(circularMultiMap.containsValue("1_2"));
        assertFalse(circularMultiMap.containsValue("2"));
    }

    @Test

    public void shouldReturnEntrySet() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);

        // when
        circularMultiMap.put("1", "1_1");
        circularMultiMap.put("1", "1_2");
        circularMultiMap.put("1", "1_3");
        circularMultiMap.put("2", "2");

        // then
        assertEquals(Sets.newHashSet(
                new CircularMultiMap.ImmutableEntry[]{
                        circularMultiMap.new ImmutableEntry("1", "1_1"),
                        circularMultiMap.new ImmutableEntry("1", "1_2"),
                        circularMultiMap.new ImmutableEntry("1", "1_3"),
                        circularMultiMap.new ImmutableEntry("2", "2")
                }), circularMultiMap.entrySet());
    }

    @Test

    public void shouldCorrectlyConstructAndGetEntryValue() {
        // when
        CircularMultiMap.ImmutableEntry immutableEntry = new CircularMultiMap<>(3, 3).new ImmutableEntry("key", "value");

        // then
        assertEquals(immutableEntry.getKey(), "key");
        assertEquals(immutableEntry.getValue(), "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void shouldNotAllowImmutableEntryToBeModified() {
        new CircularMultiMap<>(3, 3).new ImmutableEntry("key", "value").setValue("new_value");
    }

    @Test

    public void shouldSupportRemovingAllValues() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);
        circularMultiMap.put("1", "1_1");
        circularMultiMap.put("1", "1_2");
        circularMultiMap.put("1", "1_3");
        circularMultiMap.put("2", "2");

        // when
        assertEquals(Arrays.asList("1_1", "1_2", "1_3"), circularMultiMap.removeAll("1"));
        assertNull(circularMultiMap.removeAll("3"));

        // then
        // - should have correct keys
        assertFalse(circularMultiMap.containsKey("1"));
        assertTrue(circularMultiMap.containsKey("2"));
        assertEquals(Sets.newHashSet("2"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("1_1"));
        assertFalse(circularMultiMap.containsValue("1_2"));
        assertFalse(circularMultiMap.containsValue("1_3"));
        assertTrue(circularMultiMap.containsValue("2"));
        assertEquals(Arrays.asList("2"), circularMultiMap.values());
        // - should have correct values per key
        assertNull(circularMultiMap.get("1"));
        assertEquals("2", circularMultiMap.get("2"));
        assertNull(circularMultiMap.getAll("1"));
        assertEquals(Arrays.asList("2"), circularMultiMap.getAll("2"));
    }

    @Test

    public void shouldSupportRemovingAValue() {
        // given
        CircularMultiMap<String, String> circularMultiMap = new CircularMultiMap<>(3, 3);
        circularMultiMap.put("1", "1_1");
        circularMultiMap.put("1", "1_2");
        circularMultiMap.put("1", "1_3");
        circularMultiMap.put("2", "2");

        // when
        assertEquals("1_1", circularMultiMap.remove("1"));
        assertNull(circularMultiMap.remove("3"));

        // then
        // - should have correct keys
        assertTrue(circularMultiMap.containsKey("1"));
        assertTrue(circularMultiMap.containsKey("2"));
        assertEquals(Sets.newHashSet("1", "2"), circularMultiMap.keySet());
        // - should have correct values
        assertFalse(circularMultiMap.containsValue("1_1"));
        assertTrue(circularMultiMap.containsValue("1_2"));
        assertTrue(circularMultiMap.containsValue("1_3"));
        assertTrue(circularMultiMap.containsValue("2"));
        assertEquals(Arrays.asList("1_2", "1_3", "2"), circularMultiMap.values());
        // - should have correct values per key
        assertEquals("1_2", circularMultiMap.get("1"));
        assertEquals("2", circularMultiMap.get("2"));
        assertEquals(Arrays.asList("1_2", "1_3"), circularMultiMap.getAll("1"));
        assertEquals(Arrays.asList("2"), circularMultiMap.getAll("2"));
    }
}
