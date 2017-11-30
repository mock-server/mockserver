package org.mockserver.formatting;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class StringFormatter {

    public static String[] indentAndToString(Object... objects) {
        String[] indentedObjects = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
        }
        return indentedObjects;
    }
}
