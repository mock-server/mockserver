package org.mockserver.client.serialization.model;

import org.junit.Test;
import org.mockserver.verify.VerificationChain;

import java.util.Arrays;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;

public class VerificationChainDTOTest {

    @Test
    public void shouldReturnValueSetInConstructor() {
        // given
        VerificationChain verification = new VerificationChain()
                .withRequests(
                        request("one"),
                        request("two"),
                        request("three")
                );

        // when
        VerificationChainDTO verificationChainDTO = new VerificationChainDTO(verification);

        // then
        assertThat(verificationChainDTO.getHttpRequests(), is(Arrays.asList(
                new HttpRequestDTO(request("one")),
                new HttpRequestDTO(request("two")),
                new HttpRequestDTO(request("three"))
        )));
    }

    @Test
    public void shouldBuildObject() {
        // given
        VerificationChain verification = new VerificationChain()
                .withRequests(
                        request("one"),
                        request("two"),
                        request("three")
                );

        // when
        VerificationChain builtVerification = new VerificationChainDTO(verification).buildObject();

        // then
        assertThat(builtVerification.getHttpRequests(), is(Arrays.asList(
                request("one"),
                request("two"),
                request("three")
        )));
    }

    @Test
    public void shouldReturnValueSetInSetter() {
        // given
        VerificationChain verification = new VerificationChain();

        // when
        VerificationChainDTO verificationChainDTO = new VerificationChainDTO(verification);
        verificationChainDTO.setHttpRequests(Arrays.asList(
                new HttpRequestDTO(request("one")),
                new HttpRequestDTO(request("two")),
                new HttpRequestDTO(request("three"))
        ));

        // then
        assertThat(verificationChainDTO.getHttpRequests(), is(Arrays.asList(
                new HttpRequestDTO(request("one")),
                new HttpRequestDTO(request("two")),
                new HttpRequestDTO(request("three"))
        )));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        VerificationChainDTO verificationChainDTO = new VerificationChainDTO(null);

        // then
        assertThat(verificationChainDTO.getHttpRequests(), is(Arrays.<HttpRequestDTO>asList()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        VerificationChainDTO verificationChainDTO = new VerificationChainDTO(new VerificationChain());

        // then
        assertThat(verificationChainDTO.getHttpRequests(), is(Arrays.<HttpRequestDTO>asList()));
    }

}