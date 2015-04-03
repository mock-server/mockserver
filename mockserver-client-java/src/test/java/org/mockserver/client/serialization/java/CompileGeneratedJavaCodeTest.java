package org.mockserver.client.serialization.java;

import org.junit.Ignore;
import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.mock.Expectation;
import org.mockserver.model.*;

import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
@Ignore
public class CompileGeneratedJavaCodeTest {

    @Test
    public void shouldCompileGeneratedCode() throws URISyntaxException {

        String expectationAsJavaCode = new ExpectationToJavaSerializer().serializeAsJava(8,
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
                                .withBody(new StringBody("somebody")),
                        Times.once()
                ).thenRespond(
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
                        "import org.mockserver.client.server.MockServerClient;\n" +
                        "import org.mockserver.matchers.Times;\n" +
                        "import org.mockserver.mock.Expectation;\n" +
                        "import org.mockserver.model.*;\n" +
                        "import static org.mockserver.model.HttpRequest.request;\n" +
                        "import static org.mockserver.model.HttpResponse.response;\n\n" +
                        "class TestClass {\n" +
                        "   static {" +
                        "      " + expectationAsJavaCode + "\n" +
                        "   }\n" +
                        "}")
        );
    }

    public boolean compileJavaCode(final String javaCode) throws URISyntaxException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        JavaCompiler.CompilationTask task = compiler.getTask(null, compiler.getStandardFileManager(null, null, null), null, null, null,
                Arrays.asList(
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
