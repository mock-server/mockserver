package org.mockserver.verify;

import org.junit.Test;
import org.mockserver.model.HttpRequest;

import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
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

    @Test
    public void shouldSerializeToJsonString() throws Exception {
        String nl = System.getProperty("line.separator");
        assertEquals(
            "{" + nl +
            "  \"httpRequest\" : { }," + nl +
            "  \"times\" : {" + nl +
            "    \"lowerBound\" : 1" + nl +
            "  }" + nl +
            "}",
            new Verification().toString()
        );

    }
}
