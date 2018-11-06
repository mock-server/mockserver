package org.mockserver.serialization.model;

import org.junit.Test;
import org.mockserver.model.HttpRequest;
import org.mockserver.verify.Verification;
import org.mockserver.verify.VerificationTimes;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.Verification.verification;

public class VerificationDTOTest {

    @Test
    public void shouldReturnValuesSetInConstructor() {
        // given
        HttpRequest request = request();
        VerificationTimes times = VerificationTimes.atLeast(1);
        Verification verification = verification()
                .withRequest(request)
                .withTimes(times);

        // when
        VerificationDTO verificationDTO = new VerificationDTO(verification);

        // then
        assertThat(verificationDTO.getHttpRequest(), is(new HttpRequestDTO(request)));
        assertThat(verificationDTO.getTimes(), is(new VerificationTimesDTO(times)));
    }

    @Test
    public void shouldBuildObject() {
        // given
        HttpRequest request = request();
        VerificationTimes times = VerificationTimes.atLeast(1);
        Verification verification = verification()
                .withRequest(request)
                .withTimes(times);

        // when
        Verification builtVerification = new VerificationDTO(verification).buildObject();

        // then
        assertThat(builtVerification.getHttpRequest(), is(request));
        assertThat(builtVerification.getTimes(), is(times));
    }

    @Test
    public void shouldReturnValuesSetInSetter() {
        // given
        HttpRequestDTO request = new HttpRequestDTO(request());
        VerificationTimesDTO times = new VerificationTimesDTO(VerificationTimes.atLeast(1));
        Verification verification = verification();

        // when
        VerificationDTO verificationDTO = new VerificationDTO(verification);
        verificationDTO.setHttpRequest(request);
        verificationDTO.setTimes(times);

        // then
        assertThat(verificationDTO.getHttpRequest(), is(request));
        assertThat(verificationDTO.getTimes(), is(times));
    }

    @Test
    public void shouldHandleNullObjectInput() {
        // when
        VerificationDTO verificationDTO = new VerificationDTO(null);

        // then
        assertThat(verificationDTO.getHttpRequest(), is(nullValue()));
        assertThat(verificationDTO.getTimes(), is(nullValue()));
    }

    @Test
    public void shouldHandleNullFieldInput() {
        // when
        VerificationDTO verificationDTO = new VerificationDTO(new Verification());

        // then
        assertThat(verificationDTO.getHttpRequest(), is(new HttpRequestDTO(request())));
        assertThat(verificationDTO.getTimes(), is(new VerificationTimesDTO(VerificationTimes.atLeast(1))));
    }

}