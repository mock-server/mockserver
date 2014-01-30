package org.mockserver.test;


import java.util.Collection;
import java.util.List;

import static org.junit.Assert.*;

/**
 * @author jamesdbloom
 */
public class Assert {

    /**
     * Asserts that string contains specified substring.
     */
    public static void assertContains(String string, String substring) {
        assertNotNull("string should not be null", string);
        assertNotNull("substring should not be null", substring);

        if (!string.contains(substring)) {
            fail("Unable to find [" + substring + "] in [" + string + "]");
        }
    }

    /**
     * Asserts that string contains specified substring.
     */
    public static void assertDoesNotContain(String string, String substring) {
        assertNotNull("string should not be null", string);
        assertNotNull("substring should not be null", substring);

        if (string.contains(substring)) {
            fail("Able to find [" + substring + "] in [" + string + "]");
        }
    }

    /**
     * Asserts that the two lists contain the same entries regardless of order
     */
    public static <T> void assertSameEntries(Collection<T> collectionOne, Collection<T> collectionTwo) {
        assertEquals("different number of entries between list [" + collectionOne + "] and [" + collectionTwo + "]", collectionOne.size(), collectionTwo.size());

        for (T expected : collectionOne) {
            if (!collectionTwo.contains(expected)) {
                fail("list [" + collectionOne + "] does not contain [" + expected + "]");
            }
        }

        for (T expected : collectionTwo) {
            if (!collectionOne.contains(expected)) {
                fail("list [" + collectionTwo + "] does not contain [" + expected + "]");
            }
        }
    }
}
