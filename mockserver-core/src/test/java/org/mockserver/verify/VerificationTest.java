package org.mockserver.verify;

import org.junit.Test;
import org.mockserver.model.HttpRequest;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.verify.VerificationTimes.atLeast;

/**
 * @author jamesdbloom
 */
public class VerificationTest {

    @Test
    public void shouldReturnValuesSetInSetter() {
        // when
        HttpRequest request = request();
        VerificationTimes times = atLeast(2);
        Verification verification = new Verification()
                .withRequest(request)
                .withTimes(times);

        // then
        assertThat(verification.getHttpRequest(), sameInstance(request));
        assertThat(verification.getTimes(), sameInstance(times));
    }

}
