# Fix: MockServerClient.verify false positive (#1757)

## Problem

`MockServerClient.verify()` intermittently reports "Request not found at least N times" even when the required number of matching requests exist in the log. This is a race condition in the LMAX Disruptor ring buffer sequencing.

### Root Cause

When a client sends requests and then calls `verify()`, the verify RUNNABLE can be sequenced in the ring buffer *before* a request's `RECEIVED_REQUEST` log entry, even though the client received the response before calling verify. Different Netty I/O threads publish to the ring buffer concurrently with no happens-before relationship.

The error message shows the "missing" request because a *second* Disruptor RUNNABLE (`retrieveAllRequests` for the error message) runs later, by which time the request has been processed.

### Why a simple retry doesn't work

A retry would fix false negatives (verify says "not enough" when there are enough), but NOT false positives for `atMost`/`exactly` checks (verify says "OK" when requests haven't been logged yet).

## Solution: Drain Before Verify

Add a `drainDisruptor()` method that publishes a no-op RUNNABLE with a `CountDownLatch` and waits for it to complete. Call this at the start of both `verify()` overloads. This guarantees all in-flight log events have been processed before verification reads the event log.

## Changes

### 1. `mockserver/mockserver-core/src/main/java/org/mockserver/log/MockServerEventLog.java`

#### Add import

```java
import java.util.concurrent.CountDownLatch;
```

#### Add `drainDisruptor()` method (after `processLogEntry` ~line 157)

```java
private void drainDisruptor() {
    if (asynchronousEventProcessing) {
        CountDownLatch latch = new CountDownLatch(1);
        disruptor.publishEvent(new LogEntry()
            .setType(RUNNABLE)
            .setConsumer(latch::countDown)
        );
        try {
            latch.await(2, SECONDS);
        } catch (InterruptedException ignore) {
            Thread.currentThread().interrupt();
        }
    }
}
```

#### Call `drainDisruptor()` at the start of `verify(Verification, Consumer<String>)` (~line 479)

Before:
```java
public void verify(Verification verification, Consumer<String> resultConsumer) {
    final String logCorrelationId = UUIDService.getUUID();
    if (verification != null) {
```

After:
```java
public void verify(Verification verification, Consumer<String> resultConsumer) {
    drainDisruptor();
    final String logCorrelationId = UUIDService.getUUID();
    if (verification != null) {
```

#### Call `drainDisruptor()` at the start of `verify(VerificationSequence, Consumer<String>)` (~line 556)

Before:
```java
public void verify(VerificationSequence verificationSequence, Consumer<String> resultConsumer) {
    if (verificationSequence != null) {
```

After:
```java
public void verify(VerificationSequence verificationSequence, Consumer<String> resultConsumer) {
    drainDisruptor();
    if (verificationSequence != null) {
```

### 2. `mockserver/mockserver-core/src/test/java/org/mockserver/log/MockServerEventLogRequestLogEntryVerificationTest.java`

Add a test that reproduces the race condition:

```java
@Test
public void shouldPassVerificationWhenRequestLoggedConcurrently() throws Exception {
    // given - one request already logged
    HttpRequest httpRequest = new HttpRequest().withPath("some_path");
    mockServerEventLog.add(
        new LogEntry()
            .setHttpRequest(httpRequest)
            .setType(RECEIVED_REQUEST)
    );

    // when - schedule a second request to be logged with a brief delay
    // (simulates the race where request #2 is in-flight in the Disruptor
    // when verify is called)
    new Thread(() -> {
        try {
            Thread.sleep(50);
        } catch (InterruptedException ignore) {
        }
        mockServerEventLog.add(
            new LogEntry()
                .setHttpRequest(httpRequest)
                .setType(RECEIVED_REQUEST)
        );
    }).start();

    // allow the first request to be processed
    Thread.sleep(100);

    // then - verify should succeed because drainDisruptor ensures
    // all in-flight events are processed before counting
    assertThat(verify(verification().withRequest(httpRequest).withTimes(atLeast(2))), is(""));
}
```

### 3. Run tests

```bash
cd mockserver && mvn test -pl mockserver-core -Dtest=MockServerEventLogRequestLogEntryVerificationTest -DfailIfNoTests=false
```

## Impact Analysis

- **Performance**: Negligible. The drain adds one extra Disruptor publish+await per verify call. The Disruptor processes events in microseconds.
- **Backward compatibility**: No API changes. Only internal timing behavior changes.
- **Risk**: Low. The drain is a simple synchronization barrier. If the Disruptor is stopped, the 2-second timeout prevents hanging.
- **Scope**: Only affects verify operations. Normal request/response processing is unaffected.
