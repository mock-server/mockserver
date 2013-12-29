package org.mockserver.test;

import org.junit.Test;

import java.util.Arrays;

import static org.mockserver.test.Assert.*;

/**
 * @author jamesdbloom
 */
public class AssetTest {

    @Test
    public void shouldNotFailWhenStringDoesContainSubstring() {
        assertContains("string", "s");
        assertContains("string", "g");
        assertContains("string", "in");
        assertContains("string", "string");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenStringDoesNotContainSubstring() {
        assertContains("string", "invalid");
    }

    @Test
    public void shouldNotFailWhenStringDoesNotContainsSubstring() {
        assertDoesNotContain("string", "sstring");
        assertDoesNotContain("string", "stringg");
        assertDoesNotContain("string", "x");
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenStringDoesContainSubstring() {
        assertDoesNotContain("string", "in");
    }

    @Test
    public void shouldNotFailWhenListsMatch() {
        assertSameListEntries(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "3"));
        assertSameListEntries(Arrays.asList("2", "1", "3"), Arrays.asList("1", "2", "3"));
        assertSameListEntries(Arrays.asList("3", "2", "1"), Arrays.asList("1", "2", "3"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSecondListLarger() {
        assertSameListEntries(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "3", "4"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFirstListLarger() {
        assertSameListEntries(Arrays.asList("1", "2", "3", "4"), Arrays.asList("1", "2", "3"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenListsHaveOneDifferentEntry() {
        assertSameListEntries(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "4"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenListsHaveMultipleDifferentEntry() {
        assertSameListEntries(Arrays.asList("a", "b", "c"), Arrays.asList("1", "2", "3"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenListSameSizeButDifferentNumberOfEachEntry() {
        assertSameListEntries(Arrays.asList("2", "2", "2"), Arrays.asList("2", "2", "1"));
    }
}
