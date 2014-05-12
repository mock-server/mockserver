package org.mockserver.model;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class DelayTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        Delay delay = new Delay(TimeUnit.DAYS, 5);

        // then
        assertThat(delay.getTimeUnit(), is(TimeUnit.DAYS));
        assertThat(delay.getValue(), is(5l));
    }
}
