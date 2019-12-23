package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;

public class TimeToLiveSerializerTest {

    private final ObjectMapper objectMapper = createObjectMapper(new TimeToLiveSerializer());

    @Test
    public void shouldSerializeUnlimitedTimeToLive() throws JsonProcessingException {
        assertThat(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(TimeToLive.unlimited()),
            is("{" + NEW_LINE +
                "  \"unlimited\" : true" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeExactTimesToLive() throws JsonProcessingException {
        TimeToLive exactly = TimeToLive.exactly(SECONDS, 1L);
        assertThat(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exactly),
            is("{" + NEW_LINE +
                "  \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "  \"timeToLive\" : 1," + NEW_LINE +
                "  \"endDate\" : " + exactly.getEndDate() + NEW_LINE +
                "}")
        );
    }

}