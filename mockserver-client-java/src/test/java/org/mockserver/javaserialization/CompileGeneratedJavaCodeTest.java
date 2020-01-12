package org.mockserver.javaserialization;

import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;
import org.mockserver.serialization.java.ExpectationToJavaSerializer;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpClassCallback.callback;
import static org.mockserver.model.HttpForward.forward;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.HttpTemplate.template;

/**
 * @author jamesdbloom
 */
public class CompileGeneratedJavaCodeTest {

    private static final String commonImports = "" +
        "import org.mockserver.client.MockServerClient;" + NEW_LINE +
        "import org.mockserver.matchers.Times;" + NEW_LINE +
        "import org.mockserver.matchers.TimeToLive;" + NEW_LINE +
        "import org.mockserver.mock.Expectation;" + NEW_LINE +
        "import org.mockserver.model.*;" + NEW_LINE +
        "import static org.mockserver.model.HttpRequest.request;" + NEW_LINE;

    @Test
    public void shouldCompileExpectationWithHttpResponse() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serialize(1,
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(
                        new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                        new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                    )
                    .withHeaders(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
                    .withSecure(false)
                    .withKeepAlive(true)
                    .withSocketAddress("someHost", 1234, SocketAddress.Scheme.HTTP)
                    .withBody(new StringBody("somebody")),
                Times.once(),
                TimeToLive.unlimited()
            )
                .thenRespond(
                    response()
                        .withStatusCode(304)
                        .withHeaders(
                            new Header("responseHeaderNameOne", "responseHeaderValueOneOne", "responseHeaderValueOneTwo"),
                            new Header("responseHeaderNameTwo", "responseHeaderValueTwo")
                        )
                        .withCookies(
                            new Cookie("responseCookieNameOne", "responseCookieValueOne"),
                            new Cookie("responseCookieNameTwo", "responseCookieValueTwo")
                        )
                        .withBody("responseBody")
                )
        );

        assertTrue(compileJavaCode(commonImports +
            "import static org.mockserver.model.HttpResponse.response;" + NEW_LINE + NEW_LINE +
            "class TestClass {" + NEW_LINE +
            "   static {" +
            "      " + expectationAsJavaCode + "" + NEW_LINE +
            "   }" + NEW_LINE +
            "}")
        );
    }

    @Test
    public void shouldCompileExpectationWithHttpResponseTemplate() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serialize(1,
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(
                        new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                        new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                    )
                    .withHeaders(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
                    .withSecure(false)
                    .withKeepAlive(true)
                    .withBody(new StringBody("somebody")),
                Times.once(),
                TimeToLive.unlimited()
            )
                .thenRespond(
                    template(HttpTemplate.TemplateType.JAVASCRIPT)
                        .withTemplate("some_random_template")
                )
        );

        assertTrue(compileJavaCode(commonImports +
            "import static org.mockserver.model.HttpTemplate.template;" + NEW_LINE + NEW_LINE +
            "class TestClass {" + NEW_LINE +
            "   static {" +
            "      " + expectationAsJavaCode + "" + NEW_LINE +
            "   }" + NEW_LINE +
            "}")
        );
    }

    @Test
    public void shouldCompileExpectationWithHttpForward() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serialize(1,
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(
                        new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                        new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                    )
                    .withHeaders(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
                    .withSecure(false)
                    .withKeepAlive(true)
                    .withBody(new StringBody("somebody")),
                Times.once(),
                TimeToLive.unlimited()
            )
                .thenForward(
                    forward()
                        .withHost("localhost")
                        .withPort(1080)
                        .withScheme(HttpForward.Scheme.HTTPS)
                )
        );

        assertTrue(compileJavaCode(commonImports +
            "import static org.mockserver.model.HttpForward.forward;" + NEW_LINE + NEW_LINE +
            "class TestClass {" + NEW_LINE +
            "   static {" +
            "      " + expectationAsJavaCode + "" + NEW_LINE +
            "   }" + NEW_LINE +
            "}")
        );
    }

    @Test
    public void shouldCompileExpectationWithClassCallback() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serialize(1,
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(
                        new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                        new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                    )
                    .withHeaders(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
                    .withSecure(false)
                    .withKeepAlive(true)
                    .withBody(new StringBody("somebody")),
                Times.once(),
                TimeToLive.unlimited()
            )
                .thenRespond(
                    callback()
                        .withCallbackClass("some_random_class")
                )
        );

        assertTrue(compileJavaCode(commonImports +
            "import static org.mockserver.model.HttpClassCallback.callback;" + NEW_LINE + NEW_LINE +
            "class TestClass {" + NEW_LINE +
            "   static {" +
            "      " + expectationAsJavaCode + "" + NEW_LINE +
            "   }" + NEW_LINE +
            "}")
        );
    }

    @Test
    public void shouldCompileExpectationWithObjectCallback() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serialize(1,
            new Expectation(
                request()
                    .withMethod("GET")
                    .withPath("somePath")
                    .withQueryStringParameters(
                        new Parameter("requestQueryStringParameterNameOne", "requestQueryStringParameterValueOneOne", "requestQueryStringParameterValueOneTwo"),
                        new Parameter("requestQueryStringParameterNameTwo", "requestQueryStringParameterValueTwo")
                    )
                    .withHeaders(
                        new Header("requestHeaderNameOne", "requestHeaderValueOneOne", "requestHeaderValueOneTwo"),
                        new Header("requestHeaderNameTwo", "requestHeaderValueTwo")
                    )
                    .withCookies(
                        new Cookie("requestCookieNameOne", "requestCookieValueOne"),
                        new Cookie("requestCookieNameTwo", "requestCookieValueTwo")
                    )
                    .withSecure(false)
                    .withKeepAlive(true)
                    .withBody(new StringBody("somebody")),
                Times.once(),
                TimeToLive.unlimited()
            )
                .thenRespond(
                    new HttpObjectCallback()
                        .withClientId("some_random_clientId")
                )
        );

        assertTrue(compileJavaCode("" +
            "import org.mockserver.client.MockServerClient;" + NEW_LINE +
            "import org.mockserver.matchers.Times;" + NEW_LINE +
            "import org.mockserver.matchers.TimeToLive;" + NEW_LINE +
            "import org.mockserver.mock.Expectation;" + NEW_LINE +
            "import org.mockserver.model.*;" + NEW_LINE +
            "import static org.mockserver.model.HttpRequest.request;" + NEW_LINE + NEW_LINE +
            "class TestClass {" + NEW_LINE +
            "   static {" +
            "      " + expectationAsJavaCode + "" + NEW_LINE +
            "   }" + NEW_LINE +
            "}")
        );
    }

    private boolean compileJavaCode(final String javaCode) throws URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaCompiler.CompilationTask task = compiler.getTask(null, compiler.getStandardFileManager(null, null, null), null, null, null,
            Collections.singletonList(
                new SimpleJavaFileObject(new URI("TestClass"), JavaFileObject.Kind.SOURCE) {
                    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                        return javaCode;
                    }
                }
            )
        );

        return task.call();
    }
}
