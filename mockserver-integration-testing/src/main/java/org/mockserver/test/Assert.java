package org.mockserver.test;


import java.util.List;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class Assert {

    /**
     * Asserts that string contains specified substring.
     */
    public static void assertContains(String substring, String string) {
        assertNotNull("string should not be null", string);
        assertNotNull("substring should not be null", substring);

        if (!string.contains(substring)) {
            fail("Unable to find [" + substring + "] in [" + string + "]");
        }
    }

    /**
     * Asserts that string contains specified substring.
     */
    public static void assertDoesNotContain(String substring, String string) {
        assertNotNull("string should not be null", string);
        assertNotNull("substring should not be null", substring);

        if (string.contains(substring)) {
            fail("Able to find [" + substring + "] in [" + string + "]");
        }
    }

    /**
     * Asserts that the two lists contain the same entries regardless of order
     */
    public static <T> void assertSameListEntries(List<T> listOne, List<T> listTwo) {
        assertEquals("different number of entries", listOne.size(), listTwo.size());

        for (T expected : listOne) {
            if (!listTwo.contains(expected)) {
                fail("list [" + listOne + "] does not contain [" + expected + "]");
            }
        }

        for (T expected : listTwo) {
            if (!listOne.contains(expected)) {
                fail("list [" + listTwo + "] does not contain [" + expected + "]");
            }
        }
    }
}
