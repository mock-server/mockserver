package org.mockserver.matchers;

import org.junit.Before;
import org.junit.Test;
import org.mockserver.collections.CaseInsensitiveRegexMultiMap;
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
    private CaseInsensitiveRegexMultiMap multimap;
    private List<KeyToMultiValue> keyToMultiValues;

    @Before
    public void setupTestFixture() {
        multimap = new CaseInsensitiveRegexMultiMap();
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
    public void doesNotMatchEmptyValueInExpectation() {
        // given
        multimap.put("foo", "");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "bar"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingRegexValue() {
        // given
        multimap.put("foo", "b.*");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "bar"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingRegexKey() {
        // given
        multimap.put("f.*", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "bar"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingRegexValueAndKey() {
        // given
        multimap.put("f.*", "b.*");

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
    public void matchesMatchingValuesIgnoringCase() {
        // given
        multimap.put("foo1", "bar1");
        multimap.put("FOO2", "bar2");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("FOO1", "bar1"));
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
    public void matchesMatchingRegexKeysWithExtraValues() {
        // given
        multimap.put("f.*1", "bar1");
        multimap.put("f.*2", "bar2");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingRegexKeysAndValuesWithExtraValues() {
        // given
        multimap.put("f.*1", ".*1");
        multimap.put("f.*2", ".*2");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

        // then
        assertTrue(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void matchesMatchingRegexValuesIgnoringCase() {
        // given
        multimap.put("FOO1", ".*1");
        multimap.put("foo2", ".*2");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("FOO2", "bar2"));

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
    public void doesNotMatchDifferentEmptyValue() {
        // given
        multimap.put("foo", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", ""));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void doesNotMatchIncorrectRegexValue() {
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
    public void doesNotMatchIncorrectRegexKey() {
        // given
        multimap.put("g.*1", "bar1");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void doesNotMatchIncorrectRegexKeyAndValue() {
        // given
        multimap.put("g.*1", "a.*1");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo0", "bar0"));
        keyToMultiValues.add(new KeyToMultiValue("foo1", "bar1"));
        keyToMultiValues.add(new KeyToMultiValue("foo2", "bar2"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void shouldHandleIllegalRegexValuePattern() {
        // given
        multimap.put("foo", "/{}");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "/{}/"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }

    @Test
    public void shouldHandleIllegalRegexKeyPattern() {
        // given
        multimap.put("/{}", "bar");

        // when
        keyToMultiValues.add(new KeyToMultiValue("foo", "/{}/"));

        // then
        assertFalse(mapMatcher.matches(keyToMultiValues));
    }
}
