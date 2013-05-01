package org.mockserver.matchers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.mockserver.model.KeyToMultiValue;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MapMatcherTest {

    private MapMatcher<String, String> mapMatcher;
    private Multimap<String, String> multimap;
    private List<KeyToMultiValue<String, String>> keyToMultiValues;

    @Before
    public void setupTestFixture() {
        multimap = HashMultimap.create();
        mapMatcher = new MapMatcher<String, String>(multimap);
        keyToMultiValues = new ArrayList<KeyToMultiValue<String, String>>();
    }

    @Test
    public void matchesMatchingValues() {
        // given
        multimap.put("foo", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue<String, String>("foo", "bar"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingValuesWithExtraValues() {
        // given
        multimap.put("foo1", "bar1");

        // when
        keyToMultiValues.add(new KeyToMultiValue<String, String>("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue<String, String>("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue<String, String>("foo2", "bar2"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesEmptyExpectation() {
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void doesNotMatchDifferentKeys() {
        // given
        multimap.put("foo", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue<String, String>("foo2", "bar"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void doesNotMatchDifferentValues() {
        // given
        multimap.put("foo", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue<String, String>("foo", "bar2"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }
}
