package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Delay;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class DelayToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() {
        assertEquals("new Delay(TimeUnit.SECONDS, 10)",
            new DelayToJavaSerializer().serialize(1,
                new Delay(TimeUnit.SECONDS, 10)
            )
        );
    }

}
