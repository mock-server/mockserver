package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.matchers.Times.*;

/**
 * @author jamesdbloom
 */
public class TimesSerializerTest {

    private ObjectWriter objectWriter = ObjectMapperFactory.createObjectMapper(true);

    @Test
    public void shouldSerializeOnceTimes() throws JsonProcessingException {
        assertThat(
            objectWriter.writeValueAsString(once()),
            is("{" + NEW_LINE +
                "  \"remainingTimes\" : 1" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeExactlyTimes() throws JsonProcessingException {
        assertThat(
            objectWriter.writeValueAsString(exactly(1)),
            is("{" + NEW_LINE +
                "  \"remainingTimes\" : 1" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeUnlimitedTimes() throws JsonProcessingException {
        assertThat(
            objectWriter.writeValueAsString(unlimited()),
            is("{" + NEW_LINE +
                "  \"unlimited\" : true" + NEW_LINE +
                "}")
        );
    }

}
