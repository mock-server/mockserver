package org.mockserver.serialization.deserializers.condition;

import org.junit.Test;
import org.mockserver.serialization.ObjectMapperFactory;
import org.mockserver.serialization.model.VerificationTimesDTO;
import org.mockserver.verify.VerificationTimes;

import java.io.IOException;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author jamesdbloom
 */
public class VerificationTimesDTODeserializerTest {

    @Test
    public void shouldDeserializeAtLeastVerificationFormat() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"exact\":false,\"count\":3}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(3, -1))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"count\":3,\"exact\":false}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(3, -1))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"count\":3,\"exact\":true}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(3, 3))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"count\":3}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(3, -1))));
    }

    @Test
    public void shouldDeserializeBetweenVerificationFormat() throws IOException {
        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"atLeast\":1,\"atMost\":2}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(1, 2))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"atLeast\":1}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(1, -1))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"atMost\":2}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(-1, 2))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"atMost\":2,\"exact\":true}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(-1, 2))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{\"atMost\":2,\"count\":3}", VerificationTimesDTO.class),
            is(new VerificationTimesDTO(VerificationTimes.between(-1, 2))));

        assertThat(ObjectMapperFactory.createObjectMapper().readValue("{}", VerificationTimesDTO.class),
            is(nullValue()));
    }

}