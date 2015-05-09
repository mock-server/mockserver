package org.mockserver.verify;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpRequest.request;

public class VerificationSequenceTest {

    @Test
    public void shouldReturnValuesSetInSetter() {
        // when
        VerificationSequence verification = new VerificationSequence()
                .withRequests(request("one"), request("two"), request("three"));

        // then
        assertThat(verification.getHttpRequests(), is(Arrays.asList(request("one"), request("two"), request("three"))));
    }

}