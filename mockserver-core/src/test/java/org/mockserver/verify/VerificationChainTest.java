package org.mockserver.verify;

import org.junit.Test;

import java.util.Arrays;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockserver.model.HttpRequest.request;

public class VerificationChainTest {

    @Test
    public void shouldReturnValueSetInSetter() {
        // when
        VerificationChain verification = new VerificationChain()
                .withRequests(request("one"), request("two"), request("three"));

        // then
        assertThat(verification.getHttpRequests(), is(Arrays.asList(request("one"), request("two"), request("three"))));
    }

}