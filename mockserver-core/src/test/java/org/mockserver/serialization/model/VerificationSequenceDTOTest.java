package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.verify.VerificationSequence;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

public class VerificationSequenceDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        VerificationSequence verification = new VerificationSequence()
                .withRequests(
                        request("one"),
                        request("two"),
                        request("three")
                );

        // when
        VerificationSequenceDTO verificationSequenceDTO = new VerificationSequenceDTO(verification);

        // then
        assertThat(verificationSequenceDTO.getHttpRequests(), is(Arrays.asList(
                new HttpRequestDTO(request("one")),
                new HttpRequestDTO(request("two")),
                new HttpRequestDTO(request("three"))
        )));
    }

    @Test
    public void shouldBuildObject() {
        // given
        VerificationSequence verification = new VerificationSequence()
                .withRequests(
                        request("one"),
                        request("two"),
                        request("three")
                );

        // when
        VerificationSequence builtVerification = new VerificationSequenceDTO(verification).buildObject();

        // then
        assertThat(builtVerification.getHttpRequests(), is(Arrays.asList(
                request("one"),
                request("two"),
                request("three")
        )));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        VerificationSequence verification = new VerificationSequence();

        // when
        VerificationSequenceDTO verificationSequenceDTO = new VerificationSequenceDTO(verification);
        verificationSequenceDTO.setHttpRequests(Arrays.asList(
                new HttpRequestDTO(request("one")),
                new HttpRequestDTO(request("two")),
                new HttpRequestDTO(request("three"))
        ));

        // then
        assertThat(verificationSequenceDTO.getHttpRequests(), is(Arrays.asList(
                new HttpRequestDTO(request("one")),
                new HttpRequestDTO(request("two")),
                new HttpRequestDTO(request("three"))
        )));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        VerificationSequenceDTO verificationSequenceDTO = new VerificationSequenceDTO(null);

        // then
        assertThat(verificationSequenceDTO.getHttpRequests(), is(Arrays.<HttpRequestDTO>asList()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        VerificationSequenceDTO verificationSequenceDTO = new VerificationSequenceDTO(new VerificationSequence());

        // then
        assertThat(verificationSequenceDTO.getHttpRequests(), is(Arrays.<HttpRequestDTO>asList()));
    }

}