package org.mockserver.junit.jupiter;

import com.google.common.collect.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockserver.client.MockServerClient;
import org.mockserver.socket.PortFactory;
import org.mockserver.test.TestLoggerExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNot.not;

@ExtendWith({
    TestLoggerExtension.class,
})
class MockServerExtensionParallelSafetyTest {

    @Test
    void injectsClientWithStartedServer() throws InterruptedException {
        int freePort = PortFactory.findFreePort();
        List<Thread> threads = new ArrayList<>();
        CompletableFuture<String> completableFuture = new CompletableFuture<>();
        for (int i = 0; i < 1000; i++) {
            Thread thread = new Thread(() -> {
                try {
                    new ExtendedMockServerPerTestSuite().instantiateClient(ImmutableList.of(freePort));
                } catch (Throwable throwable) {
                    completableFuture.completeExceptionally(throwable);
                }
            });
            thread.start();
            threads.add(thread);
        }
        for (Thread foo : threads) {
            foo.join();
            completableFuture.complete("done");
        }
        assertThat("exception thrown", completableFuture.isCompletedExceptionally(), equalTo(false));
    }
}