package org.mockserver.serialization.serializers.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.serialization.ObjectMapperFactory.createObjectMapper;
import static org.mockserver.matchers.Times.*;

/**
 * @author jamesdbloom
 */
public class TimesSerializerTest {

    @Test
    public void shouldSerializeOnceTimes() throws JsonProcessingException {
        assertThat(
            createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(once()),
            is("{" + NEW_LINE +
                "  \"remainingTimes\" : 1" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeExactlyTimes() throws JsonProcessingException {
        assertThat(
            createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(exactly(1)),
            is("{" + NEW_LINE +
                "  \"remainingTimes\" : 1" + NEW_LINE +
                "}")
        );
    }

    @Test
    public void shouldSerializeUnlimitedTimes() throws JsonProcessingException {
        assertThat(
            createObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(unlimited()),
            is("{" + NEW_LINE +
                "  \"unlimited\" : true" + NEW_LINE +
                "}")
        );
    }

}
