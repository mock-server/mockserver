package org.mockserver.test;

import org.junit.Test;

import java.util.Arrays;

import static org.mockserver.test.Assert.*;

/**
 * @author jamesdbloom
 */
public class AssertTest {

    @Test
    @SuppressWarnings("AccessStaticViaInstance")
    public void shouldNotFailWhenStringDoesContainSubstring() {
        assertContains("string", "s");
        assertContains("string", "g");
        Assert.assertContains("string", "in");
        new Assert().assertContains("string", "string");
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
        assertSameEntries(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "3"));
        assertSameEntries(Arrays.asList("2", "1", "3"), Arrays.asList("1", "2", "3"));
        assertSameEntries(Arrays.asList("3", "2", "1"), Arrays.asList("1", "2", "3"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenSecondListLarger() {
        assertSameEntries(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "3", "4"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenFirstListLarger() {
        assertSameEntries(Arrays.asList("1", "2", "3", "4"), Arrays.asList("1", "2", "3"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenListsHaveOneDifferentEntry() {
        assertSameEntries(Arrays.asList("1", "2", "3"), Arrays.asList("1", "2", "4"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenListsHaveMultipleDifferentEntry() {
        assertSameEntries(Arrays.asList("a", "b", "c"), Arrays.asList("1", "2", "3"));
    }

    @Test(expected = AssertionError.class)
    public void shouldFailWhenListSameSizeButDifferentNumberOfEachEntry() {
        assertSameEntries(Arrays.asList("2", "2", "2"), Arrays.asList("2", "2", "1"));
    }
}
