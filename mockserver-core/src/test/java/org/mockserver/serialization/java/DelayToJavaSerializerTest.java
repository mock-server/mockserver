package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Delay;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;

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
