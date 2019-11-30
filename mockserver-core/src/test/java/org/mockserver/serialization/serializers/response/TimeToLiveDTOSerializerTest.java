package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.serialization.model.TimeToLiveDTO;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;

public class TimeToLiveDTOSerializerTest {

    private ObjectMapper objectMapper = createObjectMapper(new TimeToLiveSerializer());

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
        SECONDS.sleep(2);
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
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new TimeToLiveDTO(TimeToLive.exactly(SECONDS, 1L))),
            is("{" + NEW_LINE +
                "  \"timeUnit\" : \"SECONDS\"," + NEW_LINE +
                "  \"timeToLive\" : 1" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeUnlimitedTimeToLiveDTO() throws JsonProcessingException {
        assertThat(
            objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(new TimeToLiveDTO(TimeToLive.unlimited())),
            is("{" + NEW_LINE +
                "  \"unlimited\" : true" + NEW_LINE +
                "}")
        );
    }

}