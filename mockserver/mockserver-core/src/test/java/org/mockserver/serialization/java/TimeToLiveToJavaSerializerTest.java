package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class TimeToLiveToJavaSerializerTest {

    @Test
    public void shouldSerializeUnlimitedTimeToLiveAsJava() {
        assertEquals(NEW_LINE +
                "        TimeToLive.unlimited()",
            new TimeToLiveToJavaSerializer().serialize(1,
                TimeToLive.unlimited()
            )
        );
    }

    @Test
    public void shouldSerializeExactlyTimeToLiveAsJava() {
        assertEquals(NEW_LINE +
                "        TimeToLive.exactly(TimeUnit.SECONDS, 100L)",
            new TimeToLiveToJavaSerializer().serialize(1,
                TimeToLive.exactly(TimeUnit.SECONDS, 100L)
            )
        );
    }

}
