package org.mockserver.matchers;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.model.KeyToMultiValue;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author jamesdbloom
 */
public class MapMatcherTest {

    private MapMatcher mapMatcher;
    private Multimap<String, String> multimap;
    private List<KeyToMultiValue> keyToMultiValues;

    @Before
    public void setupTestFixture() {
        multimap = HashMultimap.create();
        mapMatcher = new MapMatcher(multimap);
        keyToMultiValues = new ArrayList<KeyToMultiValue>();
    }

    @Test
    public void matchesMatchingValues() {
        // given
        multimap.put("foo", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "bar"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesRegexMatchingValues() {
        // given
        multimap.put("foo", "b.*");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "bar"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingValuesWithExtraValues() {
        // given
        multimap.put("foo1", "bar1");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingRegexValuesWithExtraValues() {
        // given
        multimap.put("foo1", ".*1");
        multimap.put("foo2", ".*2");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

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
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void doesNotMatchDifferentValues() {
        // given
        multimap.put("foo", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "bar2"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void doesNotMatchIncorrectRegex() {
        // given
        multimap.put("foo1", "a.*1");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void shouldHandleIllegalRegexPattern() {
        // given
        multimap.put("foo", "/{}");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "/{}/"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }
}
