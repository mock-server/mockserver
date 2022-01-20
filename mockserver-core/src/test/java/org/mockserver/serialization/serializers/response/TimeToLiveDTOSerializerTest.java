package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.TimeToLiveDTO;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;

public class TimeToLiveDTOSerializerTest {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);
    private ObjectMapper objectMapper = ObjectMapperFactory.createObjectMapper();

    @Test
    public void shouldDeserializeExactTimesToLiveWithDTO() throws JsonProcessingException {
        long endDate = System.currentTimeMillis() + 1;
        assertThat(
            objectMapper.readValue("{" + NEW_LINE +
                "  \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "  \"timeToLive\" : 1," + NEW_LINE +
                "  \"endDate\" : " + endDate + NEW_LINE +
                "}", TimeToLiveDTO.class).buildObject(),
            is(TimeToLive.exactly(SECONDS, 1L).setEndDate(endDate))
        );
    }

    @Test
    public void shouldDeserializeEndDateWithDTO() throws JsonProcessingException, InterruptedException {
        long endDate = System.currentTimeMillis() + 1;
        assertThat(
            objectMapper.readValue("{" + NEW_LINE +
                "  \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "  \"timeToLive\" : 1," + NEW_LINE +
                "  \"endDate\" : " + endDate + NEW_LINE +
                "}", TimeToLiveDTO.class).buildObject().getEndDate(),
            is(endDate)
        );
    }

    @Test
    public void shouldSerializeExactTimesToLiveDTO() throws JsonProcessingException {
        assertThat(
            objectWriter.writeValueAsString(new TimeToLiveDTO(TimeToLive.exactly(SECONDS, 1L))),
            is("{" + NEW_LINE +
                "  \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "  \"timeToLive\" : 1" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeUnlimitedTimeToLiveDTO() throws JsonProcessingException {
        assertThat(
            objectWriter.writeValueAsString(new TimeToLiveDTO(TimeToLive.unlimited())),
            is("{" + NEW_LINE +
                "  \"unlimited\" : true" + NEW_LINE +
                "}")
        );
    }

}