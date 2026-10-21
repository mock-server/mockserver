package org.mockserver.templates.engine;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

public class TemplateFunctionsHostnameTest {

    @Test
    public void shouldNeverWaitForTheLookup() throws Exception {
        CountDownLatch lookupMayFinish = new CountDownLatch(1);
        CountDownLatch lookupFinished = new CountDownLatch(1);
        TemplateFunctions.HostnameResolver resolver = new TemplateFunctions.HostnameResolver("localhost", () -> {
            try {
                lookupMayFinish.await();
                return "build-agent-7";
            } finally {
                lookupFinished.countDown();
            }
        });
        // the lookup is blocked, so a read must return the fallback straight away
        assertThat(resolver.get(), is("localhost"));
        lookupMayFinish.countDown();
        assertThat(lookupFinished.await(10, TimeUnit.SECONDS), is(true));
        for (int i = 0; i < 100 && !"build-agent-7".equals(resolver.get()); i++) {
            Thread.sleep(10);
        }
        assertThat(resolver.get(), is("build-agent-7"));
    }

    @Test
    public void shouldKeepTheFallbackWhenTheLookupFails() throws Exception {
        CountDownLatch lookupFinished = new CountDownLatch(1);
        TemplateFunctions.HostnameResolver resolver = new TemplateFunctions.HostnameResolver("localhost", () -> {
            lookupFinished.countDown();
            throw new java.net.UnknownHostException("no such host");
        });
        assertThat(lookupFinished.await(10, TimeUnit.SECONDS), is(true));
        assertThat(resolver.get(), is("localhost"));
        assertThat(new TemplateFunctions.HostnameResolver("pod-1", null).get(), is("pod-1"));
    }

    @Test
    public void shouldReturnAHostnameWithoutBlocking() {
        assertThat(TemplateFunctions.hostname().isBlank(), is(false));
    }
}
