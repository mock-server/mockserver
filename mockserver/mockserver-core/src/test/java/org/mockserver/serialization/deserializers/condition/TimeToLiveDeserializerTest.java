package org.mockserver.serialization.deserializers.condition;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.TimeToLiveDTO;

import java.io.IOException;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author jamesdbloom
 */
public class TimeToLiveDeserializerTest {

    @Test
    public void shouldDeserializeTimeToLive() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"timeUnit\":\"SECONDS\",\"timeToLive\":10,\"unlimited\":false}", TimeToLiveDTO.class),
            is(new TimeToLiveDTO(TimeToLive.exactly(SECONDS, 10L))));
    }

}
