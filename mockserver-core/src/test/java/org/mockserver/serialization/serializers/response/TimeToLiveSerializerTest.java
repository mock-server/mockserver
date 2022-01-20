package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.serialization.ObjectMapperFactory;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;

public class TimeToLiveSerializerTest {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);

    @Test
    public void shouldSerializeUnlimitedTimeToLive() throws JsonProcessingException {
        assertThat(
            objectWriter.writeValueAsString(TimeToLive.unlimited()),
            is("{" + NEW_LINE +
                "  \"unlimited\" : true" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeExactTimesToLive() throws JsonProcessingException {
        TimeToLive exactly = TimeToLive.exactly(SECONDS, 1L);
        assertThat(
            objectWriter.writeValueAsString(exactly),
            is("{" + NEW_LINE +
                "  \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "  \"timeToLive\" : 1," + NEW_LINE +
                "  \"endDate\" : " + exactly.getEndDate() + NEW_LINE +
                "}")
        );
    }

}