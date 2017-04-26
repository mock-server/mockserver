package org.mockserver.collections;

import java.util.Set;

/**
 * @author jamesdbloom
 */
public class ContainIgnoreCase {

    static public boolean containsIgnoreCase(Set<String> set, String value) {
        for (String entry : set) {
            if (entry.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }
}
