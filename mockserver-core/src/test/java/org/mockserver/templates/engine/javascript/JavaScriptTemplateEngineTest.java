package org.mockserver.templates.engine.javascript;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpRequestDTO;
import org.mockserver.serialization.model.HttpResponseDTO;

import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.Is.isA;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.mockserver.character.Character.NEW_LINE;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

/**
 * @author jamesdbloom
 */
public class JavaScriptTemplateEngineTest {

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Mock
    private MockServerLogger logFormatter;

    @Before
    public void setupTestFixture() {
        initMocks(this);
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateFirstExample() {
        // given
        String template = "" +
            "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
            "    };" + NEW_LINE +
            "} else {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': request.body" + NEW_LINE +
            "    };" + NEW_LINE +
            "}";

        // when
        HttpResponse actualHttpResponse = new JavaScriptTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body"),
            HttpResponseDTO.class
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(200)
                    .withBody("{\"name\":\"value\"}")
            ));
        } else {
            assertThat(actualHttpResponse, nullValue());
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithSlowJavaScriptTemplate() {
        // given
        String template = "" +
            "for (var i = 0; i < 1000000000; i++) {" + NEW_LINE +
            "  i * i;" + NEW_LINE +
            "}" + NEW_LINE +
            "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
            "    };" + NEW_LINE +
            "} else {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': request.body" + NEW_LINE +
            "    };" + NEW_LINE +
            "}";

        // when
        HttpResponse actualHttpResponse = new JavaScriptTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/somePath")
                .withMethod("POST")
                .withBody("some_body"),
            HttpResponseDTO.class
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(200)
                    .withBody("{\"name\":\"value\"}")
            ));
        } else {
            assertThat(actualHttpResponse, nullValue());
        }
    }

    @Test
    public void shouldHandleMultipleHttpRequestsInParallel() throws InterruptedException {
        // given
        final String template = "" +
            "for (var i = 0; i < 1000000000; i++) {" + NEW_LINE +
            "  i * i;" + NEW_LINE +
            "}" + NEW_LINE +
            "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
            "    };" + NEW_LINE +
            "} else {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': request.body" + NEW_LINE +
            "    };" + NEW_LINE +
            "}";

        // when
        final JavaScriptTemplateEngine javaScriptTemplateEngine = new JavaScriptTemplateEngine(logFormatter);

        // then
        final HttpRequest request = request()
            .withPath("/somePath")
            .withMethod("POST")
            .withBody("some_body");
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            Thread[] threads = new Thread[3];
            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        assertThat(javaScriptTemplateEngine.executeTemplate(template, request,
                            HttpResponseDTO.class
                        ), is(
                            response()
                                .withStatusCode(200)
                                .withBody("{\"name\":\"value\"}")
                        ));
                    }
                });
                threads[i].start();
            }
            for (Thread thread : threads) {
                thread.join();
            }
        } else {
            assertThat(javaScriptTemplateEngine.executeTemplate(template, request,
                HttpResponseDTO.class
            ), nullValue());
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptTemplateSecondExample() {
        // given
        String template = "" +
            "if (request.method === 'POST' && request.path === '/somePath') {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 200," + NEW_LINE +
            "        'body': JSON.stringify({name: 'value'})" + NEW_LINE +
            "    };" + NEW_LINE +
            "} else {" + NEW_LINE +
            "    return {" + NEW_LINE +
            "        'statusCode': 406," + NEW_LINE +
            "        'body': request.body" + NEW_LINE +
            "    };" + NEW_LINE +
            "}";

        // when
        HttpResponse actualHttpResponse = new JavaScriptTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withBody("some_body"),
            HttpResponseDTO.class
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpResponse, is(
                response()
                    .withStatusCode(406)
                    .withBody("some_body")
            ));
        } else {
            assertThat(actualHttpResponse, nullValue());
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptForwardTemplateFirstExample() {
        // given
        String template = "" +
            "return {" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'cookies' : [ {" + NEW_LINE +
            "        'name' : request.cookies['someCookie']," + NEW_LINE +
            "        'value' : \"someCookie\"" + NEW_LINE +
            "    }, {" + NEW_LINE +
            "        'name' : \"someCookie\"," + NEW_LINE +
            "        'value' : request.cookies['someCookie']" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'keepAlive' : true," + NEW_LINE +
            "    'secure' : true," + NEW_LINE +
            "    'body' : \"some_body\"" + NEW_LINE +
            "};";

        // when
        HttpRequest actualHttpRequest = new JavaScriptTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/somePath")
                .withCookie("someCookie", "someValue")
                .withMethod("POST")
                .withBody("some_body"),
            HttpRequestDTO.class
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpRequest, is(
                request()
                    .withPath("/somePath")
                    .withCookie("someCookie", "someValue")
                    .withCookie("someValue", "someCookie")
                    .withKeepAlive(true)
                    .withSecure(true)
                    .withBody("some_body")
            ));
        } else {
            assertThat(actualHttpRequest, nullValue());
        }
    }

    @Test
    public void shouldHandleHttpRequestsWithJavaScriptForwardTemplateSecondExample() {
        // given
        String template = "" +
            "return {" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'queryStringParameters' : [ {" + NEW_LINE +
            "        'name' : \"queryParameter\"," + NEW_LINE +
            "        'values' : request.queryStringParameters['queryParameter']" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'headers' : [ {" + NEW_LINE +
            "        'name' : \"Host\"," + NEW_LINE +
            "        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
            "};";


        // when
        HttpRequest actualHttpRequest = new JavaScriptTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("some_body"),
            HttpRequestDTO.class
        );

        // then
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            assertThat(actualHttpRequest, is(
                request()
                    .withHeader("Host", "localhost:1080")
                    .withPath("/somePath")
                    .withQueryStringParameter("queryParameter", "someValue")
                    .withBody("{'name': 'value'}")
            ));
        } else {
            assertThat(actualHttpRequest, nullValue());
        }
    }

    @Test
    public void shouldHandleInvalidJavaScript() {
        // given
        String template = "{" + NEW_LINE +
            "    'path' : \"/somePath\"," + NEW_LINE +
            "    'queryStringParameters' : [ {" + NEW_LINE +
            "        'name' : \"queryParameter\"," + NEW_LINE +
            "        'values' : request.queryStringParameters['queryParameter']" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'headers' : [ {" + NEW_LINE +
            "        'name' : \"Host\"," + NEW_LINE +
            "        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
            "    } ]," + NEW_LINE +
            "    'body': \"{'name': 'value'}\"" + NEW_LINE +
            "};";
        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            exception.expect(RuntimeException.class);
            exception.expectCause(isA(ScriptException.class));
            exception.expectMessage(containsString("Exception transforming template:" + NEW_LINE +
                NEW_LINE +
                "\tfunction handle(request) {" + NEW_LINE +
                "\t" + NEW_LINE +
                "\t\t{" + NEW_LINE +
                "\t\t    'path' : \"/somePath\"," + NEW_LINE +
                "\t\t    'queryStringParameters' : [ {" + NEW_LINE +
                "\t\t        'name' : \"queryParameter\"," + NEW_LINE +
                "\t\t        'values' : request.queryStringParameters['queryParameter']" + NEW_LINE +
                "\t\t    } ]," + NEW_LINE +
                "\t\t    'headers' : [ {" + NEW_LINE +
                "\t\t        'name' : \"Host\"," + NEW_LINE +
                "\t\t        'values' : [ \"localhost:1080\" ]" + NEW_LINE +
                "\t\t    } ]," + NEW_LINE +
                "\t\t    'body': \"{'name': 'value'}\"" + NEW_LINE +
                "\t\t};" + NEW_LINE +
                "\t}" + NEW_LINE +
                NEW_LINE +
                " for request:" + NEW_LINE +
                NEW_LINE +
                "\t{" + NEW_LINE +
                "\t  \"path\" : \"/someOtherPath\"," + NEW_LINE +
                "\t  \"queryStringParameters\" : {" + NEW_LINE +
                "\t    \"queryParameter\" : [ \"someValue\" ]" + NEW_LINE +
                "\t  }," + NEW_LINE +
                "\t  \"body\" : \"some_body\"" + NEW_LINE +
                "\t}"));
        }

        // when
        new JavaScriptTemplateEngine(logFormatter).executeTemplate(template, request()
                .withPath("/someOtherPath")
                .withQueryStringParameter("queryParameter", "someValue")
                .withBody("some_body"),
            HttpRequestDTO.class
        );
    }
    
    @Test
    public void shouldrRestrictGlobalContextMultipleHttpRequestsInParallel1() throws InterruptedException, ExecutionException {
        // given
        final String template = ""
                + "var resbody = \"ok\"; " + NEW_LINE
                + "if (request.path.match(\".*1$\")) { " + NEW_LINE
                + "    resbody = \"nok\"; " + NEW_LINE
                + "}; " + NEW_LINE
                + "resp = { " + NEW_LINE
                + "    'statusCode': 200, "
                + "    'body': resbody" + NEW_LINE
                + "}; " + NEW_LINE
                + "return resp;";

        // when
        final JavaScriptTemplateEngine javaScriptTemplateEngine = new JavaScriptTemplateEngine(logFormatter);

        // then
        final HttpRequest ok = request()
                .withPath("/somePath/0")
                .withMethod("POST")
                .withBody("some_body");

        final HttpRequest nok = request()
                .withPath("/somePath/1")
                .withMethod("POST")
                .withBody("another_body");

        if (new ScriptEngineManager().getEngineByName("nashorn") != null) {
            ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(30);

            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                futures.add(newFixedThreadPool.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        assertThat(javaScriptTemplateEngine.executeTemplate(template, ok,
                                HttpResponseDTO.class
                        ), is(
                                response()
                                        .withStatusCode(200)
                                        .withBody("ok")
                        ));
                        return true;
                    }
                }));

                futures.add(newFixedThreadPool.submit(new Callable<Boolean>() {
                    @Override
                    public Boolean call() throws Exception {
                        assertThat(javaScriptTemplateEngine.executeTemplate(template, nok,
                                HttpResponseDTO.class
                        ), is(
                                response()
                                        .withStatusCode(200)
                                        .withBody("nok")
                        ));
                        return true;
                    }
                }));

            }

            for (Future<Boolean> future : futures) {
                future.get();
            }
            newFixedThreadPool.shutdown();

        } else {
            assertThat(javaScriptTemplateEngine.executeTemplate(template, ok,
                    HttpResponseDTO.class
            ), nullValue());
        }
    }

}
