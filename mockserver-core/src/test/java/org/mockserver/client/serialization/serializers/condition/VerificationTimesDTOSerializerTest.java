package org.mockserver.client.serialization.serializers.condition;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.Test;
import org.mockserver.client.serialization.ObjectMapperFactory;
import org.mockserver.client.serialization.model.VerificationTimesDTO;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockserver.verify.VerificationTimes.*;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTOSerializerTest {

    @Test
    public void shouldSerializeBetween() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    new VerificationTimesDTO(
                        between(1, 2)
                    )
                ),
            is("{\n" +
                "  \"atLeast\" : 1,\n" +
                "  \"atMost\" : 2\n" +
                "}"));
    }

    @Test
    public void shouldSerializeOnce() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    new VerificationTimesDTO(
                        once()
                    )
                ),
            is("{\n" +
                "  \"atLeast\" : 1,\n" +
                "  \"atMost\" : 1\n" +
                "}"));
    }

    @Test
    public void shouldSerializeExact() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    new VerificationTimesDTO(
                        exactly(2)
                    )
                ),
            is("{\n" +
                "  \"atLeast\" : 2,\n" +
                "  \"atMost\" : 2\n" +
                "}"));
    }

    @Test
    public void shouldSerializeAtLeast() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    new VerificationTimesDTO(
                        atLeast(2)
                    )
                ),
            is("{\n" +
                "  \"atLeast\" : 2\n" +
                "}"));
    }

    @Test
    public void shouldSerializeAtMost() throws JsonProcessingException {
        assertThat(ObjectMapperFactory
                .createObjectMapper()
                .writerWithDefaultPrettyPrinter()
                .writeValueAsString(
                    new VerificationTimesDTO(
                        atMost(2)
                    )
                ),
            is("{\n" +
                "  \"atMost\" : 2\n" +
                "}"));
    }
}