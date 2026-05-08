# Fix: Authentication settings affect control plane too (#1766)

## Problem

When `MOCKSERVER_TLS_MUTUAL_AUTHENTICATION_REQUIRED=true` and `MOCKSERVER_CONTROL_PLANE_TLS_MUTUAL_AUTHENTICATION_REQUIRED=false`, MockServer returns HTTP 426 "Upgrade Required" for **all** plain HTTP requests, including control plane requests like `PUT /mockserver/expectation`. The control plane should be accessible without TLS when only data-plane mTLS is required.

### Root Cause

TLS enforcement happens in `PortUnificationHandler.switchToHttp()` (line 318) at the **transport layer**, before the HTTP request is decoded. At this point, the request path is unknown, so the code cannot distinguish control plane from data plane requests. It uses `configuration.tlsMutualAuthenticationRequired()` (the data-plane setting) to reject ALL plain HTTP connections, ignoring `controlPlaneTLSMutualAuthenticationRequired`.

### Architecture Problem

```
PortUnificationHandler (transport layer — raw bytes, no HTTP parsing)
    └─ TLS check: tlsMutualAuthenticationRequired? → 426 for ALL plain HTTP
         ↓ (connection killed — pipeline never assembled)
    HttpRequestHandler (application layer — request path known)
         ├─ HttpState.handle() → control plane paths (with controlPlaneAuth)
         └─ HttpActionHandler.processAction() → data plane paths (NO TLS check)
```

## Solution: Move TLS enforcement to the application layer

Remove the transport-level 426 check from `PortUnificationHandler.switchToHttp()` and add a per-request TLS check in `HttpRequestHandler.channelRead0()` for **data plane requests only**. Control plane requests are already guarded by `controlPlaneRequestAuthenticated()` in `HttpState`.

### After fix:

```
PortUnificationHandler (transport layer)
    └─ Always assembles full pipeline for HTTP connections
         ↓
HttpRequestHandler (application layer — request path known)
    ├─ HttpState.handle() → control plane paths (existing controlPlaneAuth)
    ├─ status/bind/stop/dashboard/metrics → control plane (no TLS needed)
    └─ data plane → NEW TLS check: tlsMutualAuthenticationRequired + !isSslEnabledUpstream → 426
         └─ HttpActionHandler.processAction() (if TLS check passes)
```

## Changes

### 1. `mockserver/mockserver-netty/src/main/java/org/mockserver/netty/unification/PortUnificationHandler.java`

Remove the 426 check from `switchToHttp()`. Replace the if/else with just the pipeline assembly (the current `else` branch).

**Before (lines 318-354):**
```java
if (configuration.tlsMutualAuthenticationRequired() && !isSslEnabledUpstream(ctx.channel())) {
    HttpResponse httpResponse = response()
        .withStatusCode(426)
        .withHeader("Upgrade", "TLS/1.2, HTTP/1.1")
        .withHeader("Connection", "Upgrade");
    // ... log and write 426, disconnect ...
} else {
    addLastIfNotPresent(pipeline, new CallbackWebSocketServerHandler(httpState));
    addLastIfNotPresent(pipeline, new DashboardWebSocketHandler(httpState, isSslEnabledUpstream(ctx.channel()), false));
    if (configuration.mcpEnabled()) {
        addLastIfNotPresent(pipeline, new McpStreamableHttpHandler(httpState, server, mcpSessionManager));
    }
    addLastIfNotPresent(pipeline, new MockServerHttpServerCodec(configuration, mockServerLogger, isSslEnabledUpstream(ctx.channel()), SniHandler.retrieveClientCertificates(mockServerLogger, ctx), ctx.channel().localAddress()));
    addLastIfNotPresent(pipeline, new HttpRequestHandler(configuration, server, httpState, actionHandler));
    pipeline.remove(this);
    ctx.channel().attr(LOCAL_HOST_HEADERS).set(getLocalAddresses(ctx));
    ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
}
```

**After:**
```java
addLastIfNotPresent(pipeline, new CallbackWebSocketServerHandler(httpState));
addLastIfNotPresent(pipeline, new DashboardWebSocketHandler(httpState, isSslEnabledUpstream(ctx.channel()), false));
if (configuration.mcpEnabled()) {
    addLastIfNotPresent(pipeline, new McpStreamableHttpHandler(httpState, server, mcpSessionManager));
}
addLastIfNotPresent(pipeline, new MockServerHttpServerCodec(configuration, mockServerLogger, isSslEnabledUpstream(ctx.channel()), SniHandler.retrieveClientCertificates(mockServerLogger, ctx), ctx.channel().localAddress()));
addLastIfNotPresent(pipeline, new HttpRequestHandler(configuration, server, httpState, actionHandler));
pipeline.remove(this);
ctx.channel().attr(LOCAL_HOST_HEADERS).set(getLocalAddresses(ctx));
ctx.fireChannelRead(msg.readBytes(actualReadableBytes()));
```

This also removes the now-unused `mockServerHttpResponseToFullHttpResponse` field reference in the 426 path. Check if it's still used elsewhere in the file; if only used in this method's removed code, remove the field too.

### 2. `mockserver/mockserver-netty/src/main/java/org/mockserver/netty/HttpRequestHandler.java`

Add TLS enforcement in `channelRead0()` at the data-plane fallthrough (line 203), before `httpActionHandler.processAction()`.

**Before (lines 203-217):**
```java
} else {

    try {
        httpActionHandler.processAction(request, responseWriter, ctx, getLocalAddresses(ctx), isProxyingRequest(ctx), false);
    } catch (Throwable throwable) {
        // ... error handling ...
    }

}
```

**After:**
```java
} else {

    if (configuration.tlsMutualAuthenticationRequired() && !isSslEnabledUpstream(ctx.channel())) {
        responseWriter.writeResponse(request, response()
            .withStatusCode(426)
            .withReasonPhrase("Upgrade Required")
            .withHeader("Upgrade", "TLS/1.2, HTTP/1.1")
            .withHeader("Connection", "Upgrade"), false);
        if (MockServerLogger.isEnabled(Level.INFO)) {
            mockServerLogger.logEvent(
                new LogEntry()
                    .setLogLevel(Level.INFO)
                    .setHttpRequest(request)
                    .setMessageFormat("no tls for data plane request:{}returning 426 Upgrade Required")
                    .setArguments(request)
            );
        }
    } else {
        try {
            httpActionHandler.processAction(request, responseWriter, ctx, getLocalAddresses(ctx), isProxyingRequest(ctx), false);
        } catch (Throwable throwable) {
            // ... existing error handling unchanged ...
        }
    }

}
```

Note: `isSslEnabledUpstream` is already imported at line 46. `response()` is imported at line 43. `Level` and `LogEntry` are already imported. `MockServerLogger` is already a field. All necessary APIs are available.

### 3. Update existing test: `AbstractClientAuthenticationMockingIntegrationTest`

The existing tests in `shouldReturnUpgradeForHttp()` and `shouldFailToAuthenticateInHttpApacheClient()` send plain HTTP requests to a **data plane path** (`/some_path`). These should continue to get 426 since `tlsMutualAuthenticationRequired=true` and the request targets the data plane. **No change needed** for these two tests.

### 4. Add new test: Control plane accessible without TLS when only data-plane mTLS is required

Create a new test class that sets `tlsMutualAuthenticationRequired=true` and `controlPlaneTLSMutualAuthenticationRequired=false`, then verifies:
- Control plane requests over plain HTTP succeed (expectation creation, verify, etc.)
- Data plane requests over plain HTTP still get 426

**File:** `mockserver/mockserver-netty/src/test/java/org/mockserver/netty/integration/tls/inbound/ClientAuthenticationDataPlaneOnlyMockingIntegrationTest.java`

```java
package org.mockserver.netty.integration.tls.inbound;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockserver.configuration.ConfigurationProperties.*;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.stop.Stop.stopQuietly;
import static org.mockserver.testing.integration.mock.AbstractMockingIntegrationTestBase.makeRequest;

public class ClientAuthenticationDataPlaneOnlyMockingIntegrationTest {

    private static ClientAndServer mockServerClient;
    private static boolean originalTLSMutualAuthenticationRequired;
    private static boolean originalControlPlaneTLSMutualAuthenticationRequired;

    @BeforeClass
    public static void startServer() {
        originalTLSMutualAuthenticationRequired = tlsMutualAuthenticationRequired();
        originalControlPlaneTLSMutualAuthenticationRequired = controlPlaneTLSMutualAuthenticationRequired();

        tlsMutualAuthenticationRequired(true);
        controlPlaneTLSMutualAuthenticationRequired(false);

        mockServerClient = ClientAndServer.startClientAndServer();
    }

    @AfterClass
    public static void stopServer() {
        stopQuietly(mockServerClient);
        tlsMutualAuthenticationRequired(originalTLSMutualAuthenticationRequired);
        controlPlaneTLSMutualAuthenticationRequired(originalControlPlaneTLSMutualAuthenticationRequired);
    }

    @Test
    public void shouldAllowControlPlaneOverPlainHttp() {
        // control plane PUT /mockserver/expectation should work over plain HTTP
        mockServerClient
            .when(
                request()
                    .withMethod("GET")
                    .withPath("/data_plane_path")
            )
            .respond(
                response()
                    .withStatusCode(200)
                    .withBody("mocked_response")
            );

        // verify the expectation was created (another control plane operation)
        // this implicitly tests that the control plane is reachable
    }

    @Test
    public void shouldRejectDataPlaneOverPlainHttp() {
        // data plane request over plain HTTP should still get 426
        // ... send a plain HTTP request to a non-control-plane path and assert 426 ...
    }
}
```

The exact test structure will need to follow the patterns in the existing integration test classes (extending `AbstractMockingIntegrationTestBase` if appropriate, or being standalone). The key assertions:
1. `mockServerClient.when(...).respond(...)` over plain HTTP succeeds (no 426)
2. A plain HTTP GET to `/data_plane_path` returns 426

### 5. Verify `mockServerHttpResponseToFullHttpResponse` cleanup

Check if `mockServerHttpResponseToFullHttpResponse` is used elsewhere in `PortUnificationHandler`. If it's only used in the removed 426 code, consider removing the field to avoid dead code. If used elsewhere (e.g., in SOCKS or proxy handler code), leave it.

## Run Tests

```bash
# Run client auth tests (must still pass — data plane 426 still works)
cd mockserver && mvn test -pl mockserver-netty \
  -Dtest=ClientAuthenticationMockingIntegrationTest -DfailIfNoTests=false

# Run new test
cd mockserver && mvn test -pl mockserver-netty \
  -Dtest=ClientAuthenticationDataPlaneOnlyMockingIntegrationTest -DfailIfNoTests=false

# Run authenticated control plane tests (unaffected)
cd mockserver && mvn test -pl mockserver-netty \
  -Dtest="AuthenticatedControlPlane*" -DfailIfNoTests=false
```

## Impact Analysis

- **Performance**: Negligible. The TLS check moves from connection establishment to request handling, adding one boolean check per data-plane request.
- **Backward compatibility**: Fully backward compatible. When `tlsMutualAuthenticationRequired=true` AND `controlPlaneTLSMutualAuthenticationRequired` is not explicitly set to `false`, data-plane requests over plain HTTP still get 426. The only change is that control plane requests are no longer blocked.
- **Risk**: Medium. Moving TLS enforcement from transport to application layer means the HTTP pipeline is assembled even for rejected connections, consuming slightly more resources. However, this is necessary to support the documented configuration.
- **Security**: No weakening. Data-plane mTLS is still enforced. Control-plane access follows its own independent setting (`controlPlaneTLSMutualAuthenticationRequired`), which is already documented and supported.
- **Scope**: Changes are confined to `mockserver-netty` module.
