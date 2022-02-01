package org.mockserver.serialization.java;

import org.junit.Test;
import org.mockserver.model.*;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;

/**
 * @author jamesdbloom
 */
public class HttpOverrideForwardedRequestToJavaSerializerTest {

    @Test
    public void shouldSerializeFullObjectWithForwardAsJava() {
        assertEquals(NEW_LINE +
                "        forwardOverriddenRequest()" + NEW_LINE +
                "                .withRequestOverride(" + NEW_LINE +
                "                        request()" + NEW_LINE +
                "                                .withMethod(\"GET\")" + NEW_LINE +
                "                                .withPath(\"somePathOne\")" + NEW_LINE +
                "                                .withBody(new StringBody(\"responseBodyOne\"))" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withResponseOverride(" + NEW_LINE +
                "                        response()" + NEW_LINE +
                "                                .withStatusCode(304)" + NEW_LINE +
                "                                .withReasonPhrase(\"someReason\")" + NEW_LINE +
                "                                .withHeaders(" + NEW_LINE +
                "                                        new Header(\"responseHeaderNameOne\", \"responseHeaderValueOneOne\", \"responseHeaderValueOneTwo\")," + NEW_LINE +
                "                                        new Header(\"responseHeaderNameTwo\", \"responseHeaderValueTwo\")" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                                .withCookies(" + NEW_LINE +
                "                                        new Cookie(\"responseCookieNameOne\", \"responseCookieValueOne\")," + NEW_LINE +
                "                                        new Cookie(\"responseCookieNameTwo\", \"responseCookieValueTwo\")" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                                .withBody(\"responseBody\")" + NEW_LINE +
                "                                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))",
            new HttpOverrideForwardedRequestToJavaSerializer().serialize(1,
                new HttpOverrideForwardedRequest()
                    .withRequestOverride(new HttpRequest()
                        .withMethod("GET")
                        .withPath("somePathOne")
                        .withBody(new StringBody("responseBodyOne"))
                    )
                    .withResponseOverride(new HttpResponse()
                        .withStatusCode(304)
                        .withReasonPhrase("someReason")
                        .withHeaders(
                            new Header("responseHeaderNameOne", "responseHeaderValueOneOne", "responseHeaderValueOneTwo"),
                            new Header("responseHeaderNameTwo", "responseHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("responseCookieNameOne", "responseCookieValueOne"),
                            new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withBody("responseBody")
                        .withDelay(TimeUnit.MILLISECONDS, 100)
                    )
                    .withDelay(TimeUnit.MILLISECONDS, 100)
            )
        );
    }

}
