package org.mockserver.client.serialization.java;

import org.junit.Test;
import org.mockserver.matchers.Times;

import static org.junit.Assert.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class TimesToJavaSerializerTest {

    @Test
    public void shouldSerializeUnlimitedTimesAsJava() {
        assertEquals(NEW_LINE +
                        "        Times.unlimited()",
                new TimesToJavaSerializer().serialize(1,
                    Times.unlimited()
                )
        );
    }

    @Test
    public void shouldSerializeOnceTimesAsJava() {
        assertEquals(NEW_LINE +
                        "        Times.once()",
                new TimesToJavaSerializer().serialize(1,
                    Times.once()
                )
        );
    }

    @Test
    public void shouldSerializeExactlyTimesAsJava() {
        assertEquals(NEW_LINE +
                        "        Times.exactly(2)",
                new TimesToJavaSerializer().serialize(1,
                    Times.exactly(2)
                )
        );
    }

}
