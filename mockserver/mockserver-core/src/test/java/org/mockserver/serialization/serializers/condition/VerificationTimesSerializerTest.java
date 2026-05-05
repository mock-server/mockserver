package org.mockserver.serialization.serializers.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.verify.VerificationTimes.*;

/**
 * @author jamesdbloom
 */
public class VerificationTimesSerializerTest {

    @Test
    public void shouldSerializeBetween() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(
                    between(1, 2)
                ),
            is("{" + NEW_LINE +
                "  \"atLeast\" : 1," + NEW_LINE +
                "  \"atMost\" : 2" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeNever() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(
                    never()
                ),
            is("{" + NEW_LINE +
                "  \"atLeast\" : 0," + NEW_LINE +
                "  \"atMost\" : 0" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeOnce() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(
                    once()
                ),
            is("{" + NEW_LINE +
                "  \"atLeast\" : 1," + NEW_LINE +
                "  \"atMost\" : 1" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeExact() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(
                    exactly(2)
                ),
            is("{" + NEW_LINE +
                "  \"atLeast\" : 2," + NEW_LINE +
                "  \"atMost\" : 2" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeAtLeast() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(
                    atLeast(2)
                ),
            is("{" + NEW_LINE +
                "  \"atLeast\" : 2" + NEW_LINE +
                "}"));
    }

    @Test
    public void shouldSerializeAtMost() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper(true, false)
                .writeValueAsString(
                    atMost(2)
                ),
            is("{" + NEW_LINE +
                "  \"atMost\" : 2" + NEW_LINE +
                "}"));
    }
}