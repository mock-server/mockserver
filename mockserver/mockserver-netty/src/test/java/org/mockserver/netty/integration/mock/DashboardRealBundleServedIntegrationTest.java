package org.mockserver.netty.integration.mock;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockserver.httpclient.NettyHttpClient;
import org.mockserver.logging.MockServerLogger;
import org.mockserver.model.HttpResponse;
import org.mockserver.netty.MockServer;
import org.mockserver.scheduler.Scheduler;

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.configuration.Configuration.configuration;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.stop.Stop.stopQuietly;

/**
 * Guards the packaging AND serving of the REAL dashboard bundle end-to-end over a socket.
 *
 * <p>The {@code build-ui} Maven profile (in {@code mockserver-netty/pom.xml}) builds the
 * {@code mockserver-ui} Vite application and copies its hashed output into the netty jar at the
 * classpath path {@code /org/mockserver/dashboard}. {@link org.mockserver.dashboard.DashboardHandler}
 * then serves that path. Prior to this test the only dashboard-serving coverage used SYNTHETIC test
 * fixtures ({@code multibyte-test.js}, {@code favicon-fixture.svg}) placed at the SAME classpath path
 * — so a broken or missing real bundle (for example the Monaco-worker regression that shipped)
 * reddened nothing. This test asserts the actual application shell and a real hashed asset are served.</p>
 *
 * <p>Behaviour:</p>
 * <ul>
 *   <li>If the real bundle is not on the classpath and the {@code build-ui} profile is active (it is
 *       auto-activated by {@code mockserver-ui/package.json}, so always in CI), the test FAILS: the
 *       pom passes {@code -Dmockserver.dashboardBundleExpected=true} to failsafe.</li>
 *   <li>If the profile is NOT active (e.g. a developer build with {@code -P '!build-ui'}), a missing
 *       bundle SKIPS with a clear message rather than passing vacuously.</li>
 *   <li>If the real bundle IS present it FAILS CLOSED unless the genuine app shell is served AND it
 *       references a real hashed JS asset AND that asset is itself served with a JS content-type. A
 *       synthetic fixture cannot satisfy these assertions.</li>
 * </ul>
 */
public class DashboardRealBundleServedIntegrationTest {

    private static final String DASHBOARD_PATH = "/mockserver/dashboard";
    // Set by mockserver-netty's pom: true whenever the build-ui profile is active.
    static final String BUNDLE_EXPECTED_PROPERTY = "mockserver.dashboardBundleExpected";

    // The Vite build sets base=/mockserver/dashboard/ and emits hashed entry chunks as
    // assets/index-<hash>.js referenced absolutely from index.html (e.g.
    // <script type="module" ... src="/mockserver/dashboard/assets/index-DEADBEEF.js">).
    private static final Pattern HASHED_JS_ASSET =
        Pattern.compile("/mockserver/dashboard/assets/index-[A-Za-z0-9_-]+\\.js");

    private static MockServer mockServer;
    private static NettyHttpClient httpClient;
    private static EventLoopGroup clientEventLoopGroup;

    @BeforeClass
    public static void startServerAndClient() {
        clientEventLoopGroup = new NioEventLoopGroup(3, new Scheduler.SchedulerThreadFactory(
            DashboardRealBundleServedIntegrationTest.class.getSimpleName() + "-eventLoop"));
        httpClient = new NettyHttpClient(configuration(), new MockServerLogger(), clientEventLoopGroup, null, false);
        mockServer = new MockServer();
    }

    @AfterClass
    public static void stopServerAndClient() {
        stopQuietly(mockServer);
        if (clientEventLoopGroup != null) {
            clientEventLoopGroup.shutdownGracefully(0, 0, MILLISECONDS).syncUninterruptibly();
        }
    }

    @Test
    public void servesRealAppShellAndHashedJsAssetFromBuiltBundle() throws Exception {
        // given - the dashboard root, which the handler resolves to the built index.html
        HttpResponse index = send("GET", DASHBOARD_PATH);

        // The real bundle is only present when the build-ui profile ran (there is no synthetic
        // index.html fixture). When that profile is active - always in CI, where the netty IT
        // shards inherit the prebuild's classes - a missing bundle is a packaging regression and
        // must FAIL; otherwise (e.g. a developer build with -P '!build-ui') it skips.
        boolean served = index.getStatusCode() != null && index.getStatusCode() == 200;
        String missingBundle = "real dashboard bundle is not on the classpath - build with the build-ui Maven profile "
            + "(mockserver-ui must be built and copied into mockserver-netty resources); "
            + "GET " + DASHBOARD_PATH + " returned status " + index.getStatusCode();
        if (Boolean.getBoolean(BUNDLE_EXPECTED_PROPERTY)) {
            assertThat(missingBundle + " although " + BUNDLE_EXPECTED_PROPERTY + "=true", served, is(true));
        } else {
            Assume.assumeTrue(missingBundle, served);
        }

        String indexBody = index.getBodyAsString();

        // then - it is the genuine React application shell, not a synthetic fixture
        assertThat("index.html must be served as HTML",
            index.getFirstHeader(CONTENT_TYPE.toString()), containsString("text/html"));
        assertThat("served page must be the real dashboard app shell (mount point)",
            indexBody, containsString("id=\"root\""));
        assertThat("served page must be the real dashboard app shell (title)",
            indexBody, containsString("MockServer Dashboard"));

        // and - the app shell references a real hashed JS entry chunk (fail closed if the built
        // index.html carries no hashed bundle reference, i.e. the bundle is broken)
        Matcher matcher = HASHED_JS_ASSET.matcher(indexBody);
        assertThat(
            "built index.html must reference a hashed JS bundle (assets/index-<hash>.js); "
                + "a missing reference means the served bundle is broken. Body was:\n" + indexBody,
            matcher.find(), is(true));
        String hashedAssetPath = matcher.group();

        // and - that referenced hashed asset is itself actually served (fail closed if the copy step
        // dropped it or the handler cannot serve it - the exact class of packaging/serving regression
        // the synthetic-fixture coverage missed)
        HttpResponse asset = send("GET", hashedAssetPath);
        assertThat("hashed JS asset " + hashedAssetPath + " must be served with status 200",
            asset.getStatusCode(), is(200));
        String assetContentType = asset.getFirstHeader(CONTENT_TYPE.toString());
        assertThat("hashed JS asset must carry a JS content-type",
            assetContentType, is(notNullValue()));
        assertThat("hashed JS asset must carry a JS content-type, was: " + assetContentType,
            assetContentType, containsString("application/javascript"));
        assertThat("hashed JS asset body must be non-empty",
            asset.getBodyAsString().length(), is(greaterThan(0)));
    }

    private HttpResponse send(String method, String path) throws Exception {
        return httpClient.sendRequest(
            request()
                .withMethod(method)
                .withHeader(HOST.toString(), "localhost:" + mockServer.getLocalPort())
                .withPath(path)
        ).get(15, TimeUnit.SECONDS);
    }
}
