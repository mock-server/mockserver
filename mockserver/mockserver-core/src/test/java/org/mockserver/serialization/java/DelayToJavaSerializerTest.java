package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.Delay;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;

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

    @Test
    public void shouldSerializeUniformDistributionAsJava() {
        assertEquals("Delay.uniform(TimeUnit.MILLISECONDS, 100, 500)",
            new DelayToJavaSerializer().serialize(1,
                Delay.uniform(TimeUnit.MILLISECONDS, 100, 500)
            )
        );
    }

    @Test
    public void shouldSerializeLogNormalDistributionAsJava() {
        assertEquals("Delay.logNormal(TimeUnit.MILLISECONDS, 200, 800)",
            new DelayToJavaSerializer().serialize(1,
                Delay.logNormal(TimeUnit.MILLISECONDS, 200, 800)
            )
        );
    }

    @Test
    public void shouldSerializeGaussianDistributionAsJava() {
        assertEquals("Delay.gaussian(TimeUnit.MILLISECONDS, 200, 50)",
            new DelayToJavaSerializer().serialize(1,
                Delay.gaussian(TimeUnit.MILLISECONDS, 200, 50)
            )
        );
    }

    @Test
    public void shouldSerializeNullDelay() {
        assertEquals("",
            new DelayToJavaSerializer().serialize(1, null)
        );
    }
}
