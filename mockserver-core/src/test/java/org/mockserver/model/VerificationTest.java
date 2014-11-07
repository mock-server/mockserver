package org.mockserver.model;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.same;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.VerificationTimes.atLeast;

/**
 * @author jamesdbloom
 */
public class VerificationTest {

    @Test
    public void shouldReturnValueSetInSetter() {
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
