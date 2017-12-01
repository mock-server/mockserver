package org.mockserver.formatting;

import org.mockserver.model.HttpRequest;
import org.mockserver.templates.engine.model.HttpRequestTemplateObject;

import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class StringFormatter {

    public static String[] indentAndToString(Object... objects) {
        String[] indentedObjects = new String[objects.length];
        for (int i = 0; i < objects.length; i++) {
            if (objects[i] instanceof HttpRequest) {
                indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(new HttpRequestTemplateObject((HttpRequest) objects[i])).replaceAll("(?m)^", "\t") + NEW_LINE;
            } else {
                indentedObjects[i] = NEW_LINE + NEW_LINE + String.valueOf(objects[i]).replaceAll("(?m)^", "\t") + NEW_LINE;
            }
        }
        return indentedObjects;
    }
}
