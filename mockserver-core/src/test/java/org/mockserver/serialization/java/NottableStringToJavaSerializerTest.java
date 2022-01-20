package org.mockserver.serialization.java;

import org.junit.Test;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.model.NottableString.string;

/**
 * @author jamesdbloom
 */
public class NottableStringToJavaSerializerTest {

    @Test
    public void shouldSerializeNottedString() {
        assertEquals("not(\"some_value\")",
            NottableStringToJavaSerializer.serialize(string("some_value", true), false)
        );
    }

    @Test
    public void shouldSerializeString() {
        assertEquals("\"some_value\"",
            NottableStringToJavaSerializer.serialize(string("some_value", false), false)
        );
    }

}
