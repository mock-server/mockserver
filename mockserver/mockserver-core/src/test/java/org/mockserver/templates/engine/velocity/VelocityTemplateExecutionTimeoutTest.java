package org.mockserver.templates.engine.velocity;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.Test;
import org.mockserver.log.model.LogEntry;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.serialization.model.HttpResponseDTO;
import org.mockserver.templates.engine.velocity.directives.TimeLimitedForeach;
import org.slf4j.event.Level;

import java.io.StringWriter;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.fail;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

public class VelocityTemplateExecutionTimeoutTest {

    // two nested loops over the largest range: about 4.6e18 iterations, so it never finishes on its own
    private static final String LOOPS_FOREVER = "#foreach($i in [1..2147483647])#foreach($j in [1..2147483647])#end#end";
    private static final String RESPONSE = "{ 'statusCode': 200, 'body': 'done' }";
    private static final long HANG_GUARD_SECONDS = 60;

    private final HttpRequest request = request().withPath("/somePath");

    @Test
    public void shouldStopResponseTemplateThatLoopsForever() {
        List<LogEntry> logged = new ArrayList<>();
        VelocityTemplateEngine engine = new VelocityTemplateEngine(recordingLogger(logged), configuration().velocityTemplateExecutionTimeout(250L));

        long startNanos = System.nanoTime();
        Throwable failure = failureOf(() -> engine.executeTemplate(LOOPS_FOREVER + RESPONSE, request, HttpResponseDTO.class));
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
        assertThat(failure.getMessage(), containsString("exceeded the configured timeout of 250ms"));
        assertThat(failure.getMessage(), containsString("mockserver.velocityTemplateExecutionTimeout"));
        assertThat(elapsedMillis, lessThan(30_000L));
        assertThat(logged.stream().filter(entry -> entry.getLogLevel() == Level.WARN).map(LogEntry::getMessageFormat).toList(), hasItem(failure.getMessage()));
    }

    @Test
    public void shouldStopTextTemplateThatLoopsForever() {
        VelocityTemplateEngine engine = new VelocityTemplateEngine(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(250L));

        Throwable failure = failureOf(() -> engine.renderTemplate("start " + LOOPS_FOREVER + " end", request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldStopLoopInsideMacroAndEvaluate() {
        VelocityTemplateEngine engine = new VelocityTemplateEngine(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(250L));
        String template = "#macro(spin)#evaluate('" + LOOPS_FOREVER + "')#end#spin()" + RESPONSE;

        Throwable failure = failureOf(() -> engine.executeTemplate(template, request, HttpResponseDTO.class));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldStopBranchingMacroRecursion() {
        // ten calls per level, twelve levels deep: 10^12 macro calls, all below the macro depth limit
        String tenCalls = "#r(\"${s}x\")".repeat(10);
        String template = "#macro(r $s)#if($s.length() < 12)" + tenCalls + "#end#end#r('')done";

        Throwable failure = failureOf(() -> engineWithTimeout(250L).renderTemplate(template, request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldStopBranchingMacroRecursionWithoutMethodCalls() {
        // as above, but the depth test is a string comparison, so no Java method is called at all
        String tenCalls = "#r(\"${s}x\")".repeat(10);
        String template = "#macro(r $s)#if($s != 'xxxxxxxxxxxx')" + tenCalls + "#end#end#r('')done";

        Throwable failure = failureOf(() -> engineWithTimeout(250L).renderTemplate(template, request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldStopTemplateThatParsesItselfRepeatedly() {
        // a new engine registers its first template as "t0", so this template parses itself ten times at each of
        // eight levels; the list keeps it below the #parse depth limit, which would only log and skip
        String template = "#if(!$stack)#set($stack = [])#end#if($stack.size() < 8)#set($pushed = $stack.add(1))"
            + "#parse('t0')".repeat(10) + "#set($popped = $stack.remove(0))#end";

        Throwable failure = failureOf(() -> engineWithTimeout(250L).renderTemplate(template, request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldStopTemplateThatEvaluatesItselfRepeatedly() {
        // a list used as a depth counter keeps the recursion twelve deep, with ten evaluations per level
        String body = "#if($stack.size() < 12)#set($pushed = $stack.add(1))" + "#evaluate($self)".repeat(10) + "#set($popped = $stack.remove(0))#end";
        String template = "#set($stack = [])#set($self = '" + body + "')#evaluate($self)done";

        Throwable failure = failureOf(() -> engineWithTimeout(250L).renderTemplate(template, request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldStopChainOfDefinedBlocks() {
        // each block uses the one before ten times, so rendering $a20 means about 10^20 block renders
        StringBuilder template = new StringBuilder("#define($a0)#end");
        for (int level = 1; level <= 20; level++) {
            template.append("#define($a").append(level).append(")").append(("$a" + (level - 1)).repeat(10)).append("#end");
        }
        template.append("$a20 done");

        Throwable failure = failureOf(() -> engineWithTimeout(250L).renderTemplate(template.toString(), request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    @Test
    public void shouldReportTimeoutOfDefinedBlockReachedThroughAMethodCall() {
        // Velocity wraps an exception thrown inside a method call, here the block's toString(), in a MethodInvocationException
        List<LogEntry> logged = new ArrayList<>();
        VelocityTemplateEngine engine = new VelocityTemplateEngine(recordingLogger(logged), configuration().velocityTemplateExecutionTimeout(250L));
        StringBuilder template = new StringBuilder("#define($a0)#end");
        for (int level = 1; level <= 20; level++) {
            template.append("#define($a").append(level).append(")").append(("$a" + (level - 1)).repeat(10)).append("#end");
        }
        template.append("$a20.toString() done");

        Throwable failure = failureOf(() -> engine.renderTemplate(template.toString(), request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
        assertThat(failure.getMessage(), startsWith("Velocity template execution exceeded the configured timeout of 250ms"));
        assertThat(logged.stream().filter(entry -> entry.getLogLevel() == Level.WARN).map(LogEntry::getMessageFormat).toList(), hasItem(failure.getMessage()));
    }

    @Test
    public void shouldStopDryRunTemplateWhenTimeoutDisabled() {
        VelocityTemplateEngine engine = VelocityTemplateEngine.forDryRun(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(-1L), 250L);

        Throwable failure = failureOf(() -> engine.executeTemplate(LOOPS_FOREVER + RESPONSE, request, HttpResponseDTO.class));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
        assertThat(failure.getMessage(), containsString("velocityTemplateExecutionTimeout is 0 or less"));
        assertThat(failure.getMessage(), containsString("a dry run always stops a Velocity template after 250ms"));
        assertThat(failure.getMessage(), not(containsString("set it to 0")));
    }

    @Test
    public void shouldUseConfiguredTimeoutForDryRunWhenEnabled() {
        VelocityTemplateEngine engine = VelocityTemplateEngine.forDryRun(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(250L), 60_000L);

        Throwable failure = failureOf(() -> engine.renderTemplate(LOOPS_FOREVER, request));

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
        assertThat(failure.getMessage(), containsString("exceeded the configured timeout of 250ms"));
        assertThat(failure.getMessage(), containsString("increase mockserver.velocityTemplateExecutionTimeout"));
        // 0 does not switch off a dry run's limit, so that advice would be wrong
        assertThat(failure.getMessage(), not(containsString("set it to 0")));
    }

    @Test
    public void shouldRenderDefinedBlockThatFinishesWithinTimeout() {
        String template = "#define($greeting)hello $name#end#set($name = 'a')$greeting #set($name = 'b')$greeting";

        String rendered = withHangGuard(() -> engineWithTimeout(5_000L).renderTemplate(template, request));

        assertThat(rendered, is("hello a hello b"));
    }

    @Test
    public void shouldRenderMacrosParseAndEvaluateThatFinishWithinTimeout() {
        String template = "#macro(greet $name)hello $name#end#greet('a') #evaluate('#greet(\"b\")')";

        String rendered = withHangGuard(() -> engineWithTimeout(5_000L).renderTemplate(template, request));

        assertThat(rendered, is("hello a hello b"));
    }

    @Test
    public void shouldRenderLoopsThatFinishWithinTimeout() {
        VelocityTemplateEngine engine = new VelocityTemplateEngine(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(5_000L));
        String template = "{ 'statusCode': 200, 'body': '#foreach($i in [1..3])$i#if($foreach.hasNext),#end#end' }";

        HttpResponse response = engine.executeTemplate(template, request, HttpResponseDTO.class);

        assertThat(response, is(response().withStatusCode(200).withBody("1,2,3")));
    }

    @Test
    public void shouldNotStopLongLoopWhenTimeoutDisabled() {
        // 1ms stops this loop, so finishing it shows that 0 switches the limit off
        String longLoop = "#foreach($i in [1..3000000])#end";
        Throwable stopped = failureOf(() -> new VelocityTemplateEngine(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(1L))
            .executeTemplate(longLoop + RESPONSE, request, HttpResponseDTO.class));
        assertThat(stopped, instanceOf(VelocityTemplateTimeoutException.class));

        HttpResponse response = withHangGuard(() -> new VelocityTemplateEngine(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(0L))
            .executeTemplate(longLoop + RESPONSE, request, HttpResponseDTO.class));

        assertThat(response, is(response().withStatusCode(200).withBody("done")));
    }

    @Test
    public void shouldRestoreEnclosingDeadlineWhenInnerScopeCloses() {
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.setProperty(RuntimeConstants.CUSTOM_DIRECTIVES, TimeLimitedForeach.class.getName());
        velocityEngine.init();

        Throwable failure = failureOf(() -> {
            try (RenderDeadline.Scope outer = RenderDeadline.start(250L)) {
                try (RenderDeadline.Scope inner = RenderDeadline.start(0L)) {
                    // a nested render without a limit ends here, so the enclosing render's limit applies again
                }
                velocityEngine.evaluate(new VelocityContext(), new StringWriter(), "nested", LOOPS_FOREVER);
                return null;
            }
        });

        assertThat(failure, instanceOf(VelocityTemplateTimeoutException.class));
    }

    private static VelocityTemplateEngine engineWithTimeout(long timeoutMillis) {
        return new VelocityTemplateEngine(new MockServerLogger(), configuration().velocityTemplateExecutionTimeout(timeoutMillis));
    }

    private static Throwable failureOf(Supplier<?> render) {
        try {
            withHangGuard(render);
        } catch (RuntimeException failure) {
            return failure;
        }
        throw new AssertionError("the template finished without failing");
    }

    /**
     * Renders on a daemon thread and fails after {@link #HANG_GUARD_SECONDS}: a Velocity render cannot be
     * interrupted, so without the limit the loop would otherwise hang the test.
     */
    private static <T> T withHangGuard(Supplier<T> render) {
        CompletableFuture<T> result = new CompletableFuture<>();
        Thread thread = new Thread(() -> {
            try {
                result.complete(render.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        }, "velocity-timeout-test");
        thread.setDaemon(true);
        thread.start();
        try {
            return result.get(HANG_GUARD_SECONDS, TimeUnit.SECONDS);
        } catch (ExecutionException executionException) {
            if (executionException.getCause() instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new AssertionError(executionException.getCause());
        } catch (TimeoutException timeoutException) {
            fail("template was still running after " + HANG_GUARD_SECONDS + "s");
            return null;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interruptedException);
        }
    }

    private static MockServerLogger recordingLogger(List<LogEntry> logged) {
        return new MockServerLogger() {
            @Override
            public void logEvent(LogEntry logEntry) {
                synchronized (logged) {
                    logged.add(logEntry);
                }
            }
        };
    }
}
