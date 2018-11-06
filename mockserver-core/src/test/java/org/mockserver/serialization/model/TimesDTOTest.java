package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.matchers.Times;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;

/**
 * @author jamesdbloom
 */
public class TimesDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // when
        org.mockserver.serialization.model.TimesDTO times = new org.mockserver.serialization.model.TimesDTO(Times.exactly(5));

        // then
        assertThat(times.getRemainingTimes(), is(5));
        assertThat(times.isUnlimited(), is(false));
    }

    @Test
    public void shouldBuildCorrectObject() {
        // when
        Times times = new org.mockserver.serialization.model.TimesDTO(Times.unlimited()).buildObject();

        // then
        assertThat(times.isUnlimited(), is(true));

        // when
        times = new org.mockserver.serialization.model.TimesDTO(Times.exactly(5)).buildObject();

        // then
        assertThat(times.getRemainingTimes(), is(5));
        assertThat(times.isUnlimited(), is(false));
    }
}
