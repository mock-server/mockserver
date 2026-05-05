package org.mockserver.serialization.java;

import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.mockserver.model.*;

import java.util.concurrent.TimeUnit;

import static junit.framework.TestCase.assertEquals;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.Cookie.cookie;
import static org.mockserver.model.Header.header;
import static org.mockserver.model.HttpRequestModifier.requestModifier;
import static org.mockserver.model.HttpResponseModifier.responseModifier;
import static org.mockserver.model.Parameter.param;

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
                "                                .withProtocol(Protocol.HTTP_2)" + NEW_LINE +
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
                        .withProtocol(Protocol.HTTP_2)
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

    @Test
    public void shouldSerializeObjectWithForwardAndEmptyListsAsJava() {
        assertEquals(NEW_LINE +
                "        forwardOverriddenRequest()" + NEW_LINE +
                "                .withRequestOverride(" + NEW_LINE +
                "                        request()" + NEW_LINE +
                "                                .withMethod(\"GET\")" + NEW_LINE +
                "                                .withPath(\"somePathOne\")" + NEW_LINE +
                "                                .withBody(new StringBody(\"responseBodyOne\"))" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withRequestModifier(" + NEW_LINE +
                "                        requestModifier()" + NEW_LINE +
                "                                .withPath(\"adsdasd\",\"null\")" + NEW_LINE +
                "                                .withQueryStringParameters(" + NEW_LINE +
                "                                        null," + NEW_LINE +
                "                                        parameters(" + NEW_LINE +
                "                                                new Parameter(\"replaceNameOne\", \"replaceValueOne\")," + NEW_LINE +
                "                                                new Parameter(\"replaceNameTwo\", \"replaceValueTwo\")" + NEW_LINE +
                "                                        )," + NEW_LINE +
                "                                        null" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                                .withHeaders(" + NEW_LINE +
                "                                        null," + NEW_LINE +
                "                                        null," + NEW_LINE +
                "                                        null" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                                .withCookies(" + NEW_LINE +
                "                                        cookies(" + NEW_LINE +
                "                                                new Cookie(\"replaceNameOne\", \"replaceValueOne\")," + NEW_LINE +
                "                                                new Cookie(\"replaceNameTwo\", \"replaceValueTwo\")" + NEW_LINE +
                "                                        )," + NEW_LINE +
                "                                        null," + NEW_LINE +
                "                                        null" + NEW_LINE +
                "                                )" + NEW_LINE +
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
                "                .withResponseModifier(" + NEW_LINE +
                "                        responseModifier()" + NEW_LINE +
                "                                .withHeaders(" + NEW_LINE +
                "                                        null," + NEW_LINE +
                "                                        headers(" + NEW_LINE +
                "                                                new Header(\"addNameOne\", \"addValueOne\")," + NEW_LINE +
                "                                                new Header(\"addNameTwo\", \"addValueTwo\")" + NEW_LINE +
                "                                        )," + NEW_LINE +
                "                                        null" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                                .withCookies(" + NEW_LINE +
                "                                        cookies(" + NEW_LINE +
                "                                                new Cookie(\"replaceNameOne\", \"replaceValueOne\")," + NEW_LINE +
                "                                                new Cookie(\"replaceNameTwo\", \"replaceValueTwo\")" + NEW_LINE +
                "                                        )," + NEW_LINE +
                "                                        null," + NEW_LINE +
                "                                        null" + NEW_LINE +
                "                                )" + NEW_LINE +
                "                )" + NEW_LINE +
                "                .withDelay(new Delay(TimeUnit.MILLISECONDS, 100))",
            new HttpOverrideForwardedRequestToJavaSerializer().serialize(1,
                new HttpOverrideForwardedRequest()
                    .withRequestOverride(new HttpRequest()
                        .withMethod("GET")
                        .withPath("somePathOne")
                        .withBody(new StringBody("responseBodyOne"))
                    )
                    .withRequestModifier(
                        requestModifier()
                            .withPath("adsdasd", null)
                            .withHeaders(null, null, ImmutableList.of())
                            .withCookies(
                                ImmutableList.of(
                                    cookie("replaceNameOne", "replaceValueOne"),
                                    cookie("replaceNameTwo", "replaceValueTwo")
                                ),
                                null,
                                null
                            )
                            .withQueryStringParameters(
                                null,
                                ImmutableList.of(
                                    param("replaceNameOne", "replaceValueOne"),
                                    param("replaceNameTwo", "replaceValueTwo")
                                ),
                                null
                            )
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
                    .withResponseModifier(
                        responseModifier()
                            .withHeaders(
                                null,
                                ImmutableList.of(
                                    header("addNameOne", "addValueOne"),
                                    header("addNameTwo", "addValueTwo")
                                ),
                                ImmutableList.of()
                            )
                            .withCookies(
                                ImmutableList.of(
                                    cookie("replaceNameOne", "replaceValueOne"),
                                    cookie("replaceNameTwo", "replaceValueTwo")
                                ),
                                null,
                                null
                            )
                    )
                    .withDelay(TimeUnit.MILLISECONDS, 100)
            )
        );
    }

}
