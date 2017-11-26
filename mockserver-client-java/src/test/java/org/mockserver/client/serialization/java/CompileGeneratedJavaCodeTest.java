package org.mockserver.client.serialization.java;

import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.matchers.TimeToLive;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.Cookie;
import org.mockserver.model.Header;
import org.mockserver.model.Parameter;
import org.mockserver.model.StringBody;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
@Ignore("ignored due to issue with classpath during maven build")
public class CompileGeneratedJavaCodeTest {

    @Test
    public void shouldCompileGeneratedCode() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serializeAsJava(1,
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

        assertTrue(compileJavaCode("" +
                        "import org.mockserver.client.server.MockServerClient;" + NEW_LINE +
                        "import org.mockserver.matchers.Times;" + NEW_LINE +
                        "import org.mockserver.mock.Expectation;" + NEW_LINE +
                        "import org.mockserver.model.*;" + NEW_LINE +
                        "import static org.mockserver.model.HttpRequest.request;" + NEW_LINE +
                        "import static org.mockserver.model.HttpResponse.response;" + NEW_LINE + NEW_LINE +
                        "class TestClass {" + NEW_LINE +
                        "   static {" +
                        "      " + expectationAsJavaCode + "" + NEW_LINE +
                        "   }" + NEW_LINE +
                        "}")
        );
    }

    public boolean compileJavaCode(final String javaCode) throws URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaCompiler.CompilationTask task = compiler.getTask(null, compiler.getStandardFileManager(null, null, null), null, null, null,
                Collections.singletonList(
                        new SimpleJavaFileObject(new URI("TestClass"), JavaFileObject.Kind.SOURCE) {
                            public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                                return javaCode;
                            }
                        }
                )
        );

        return task.call();
    }
}
