package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.Times;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class TimesDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // when
        TimesDTO times = new TimesDTO(Times.exactly(5));

        // then
        assertThat(times.getRemainingTimes(), is(5));
        assertThat(times.isUnlimited(), is(false));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        Times times = new TimesDTO(Times.unlimited()).buildObject();

        // then
        assertThat(times.isUnlimited(), is(true));

        // when
        times = new TimesDTO(Times.exactly(5)).buildObject();

        // then
        assertThat(times.getRemainingTimes(), is(5));
        assertThat(times.isUnlimited(), is(false));
    }
}
