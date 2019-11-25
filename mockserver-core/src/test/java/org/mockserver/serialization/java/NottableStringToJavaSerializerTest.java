package org.mockserver.serialization.java;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringToJavaSerializerTest {

    @Test
    public void shouldSerializeNottedString() {
        assertEquals("not(\"some_value\")",
            NottableStringToJavaSerializer.serialize(
                string("some_value", true)
            )
        );
    }

    @Test
    public void shouldSerializeString() {
        assertEquals("\"some_value\"",
            NottableStringToJavaSerializer.serialize(
                string("some_value", false)
            )
        );
    }

}
