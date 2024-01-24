package org.mockserver.character;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.yaml.snakeyaml.error.MarkedYAMLException;

/**
 * @author jamesdbloom
 */
public class Character {

    public static final String NEW_LINE = System.getProperty("line.separator");

    /**
     * Some libraries use the hardcoded Unix line separator ('\n') in their exception messages.
     *
     * @see JsonProcessingException#getMessage() jackson exception
     * @see MarkedYAMLException#toString() snakeyaml exception
     */
    public static final String UNIX_LINE_SEPARATOR = "\n";

}
