package org.mockserver.dashboard;

import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockserver.model.HttpResponse;

import java.nio.charset.StandardCharsets;

import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaderNames.CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockserver.mock.HttpState.PATH_PREFIX;
import static org.mockserver.model.HttpRequest.request;

/**
 * Regression test for issue #2347: the dashboard rendered a white page when the
 * JAR was built on a UTF-8 platform (macOS) but run on a platform whose default
 * charset is not UTF-8 (Windows, where the legacy default is windows-1252). The
 * dashboard's static assets are always written to the wire as UTF-8 (the
 * Content-Type carries {@code charset=UTF-8}), but the Content-Length header was
 * computed with the JVM default charset. For an asset containing multi-byte
 * characters the two byte counts differ, so the browser truncated the bundle and
 * the dashboard failed to render.
 */
public class DashboardHandlerTest {

    @Test
    public void contentLengthMatchesUtf8BodyLengthForMultiByteAsset() throws Exception {
        // given - a dashboard asset that contains multi-byte UTF-8 characters
        HttpResponse response = renderDashboardResource("/multibyte-test.js");

        // then - the asset was found and served
        String body = response.getBodyAsString();
        assertThat(body, is(notNullValue()));

        // and - the fixture genuinely exercises the multi-byte path. We need the byte
        // length under UTF-8 to differ from the byte length under a single-byte legacy
        // charset (ISO-8859-1, the family of windows-1252 that was the JVM default on the
        // platforms where #2347 manifested). If these two lengths were equal the asset
        // would not distinguish a UTF-8 encode from a legacy-charset encode and the guard
        // below could not detect the regression on a non-UTF-8 host.
        int utf8ByteLength = body.getBytes(UTF_8).length;
        int legacyByteLength = body.getBytes(StandardCharsets.ISO_8859_1).length;
        assertThat(
            "fixture must contain genuine multi-byte UTF-8 characters so the UTF-8 byte length "
                + "differs from a single-byte legacy charset (got utf8=" + utf8ByteLength
                + ", iso-8859-1=" + legacyByteLength + ")",
            utf8ByteLength, is(greaterThan(legacyByteLength)));

        // and - the declared Content-Length equals the number of bytes actually written to
        // the wire (UTF-8), and is specifically NOT the byte count that a legacy
        // default-charset encode would have produced.
        //
        // This is the exact invariant violated by #2347: before the fix the header was
        // computed with content.getBytes() (the JVM DEFAULT charset). On a non-UTF-8 host
        // (e.g. windows-1252) a revert to that code would declare legacyByteLength, so the
        // inequality assertion below would fail and the regression IS caught in CI.
        //
        // HONEST LIMITATION: on a UTF-8-default JVM (this CI host, and any Java 18+ JVM per
        // JEP 400 regardless of platform) content.getBytes() also yields the UTF-8 length,
        // so a default-charset revert produces an identical value and CANNOT be detected by
        // a pure value assertion without changing production to expose the encode charset.
        // The inequality-vs-legacy assertion is therefore the strongest host-independent
        // guard achievable here: it converts the bug into a deterministic CI failure on
        // exactly the platforms (non-UTF-8 default charset) where #2347 actually occurs.
        long declaredContentLength = Long.parseLong(response.getFirstHeader(CONTENT_LENGTH.toString()));
        assertThat(declaredContentLength, is((long) utf8ByteLength));
        assertThat(
            "Content-Length must be the UTF-8 byte count actually written to the wire, not the "
                + "byte count of a legacy default-charset encode (#2347)",
            declaredContentLength, is(not((long) legacyByteLength)));
    }

    /**
     * Regression test for issue #2358: the dashboard references {@code favicon.svg}, but the
     * {@code svg} extension had no entry in the MIME map, so {@code MIME_MAP.get("svg")} returned
     * null and the served response carried a {@code Content-Type} header with a null value. That
     * null value crashed Netty's header encoder ("NullPointerException: value") when the response
     * was written, so the asset failed to load. The Content-Type must now be a valid, non-null
     * {@code image/svg+xml}.
     */
    @Test
    public void servesSvgAssetWithNonNullSvgContentType() throws Exception {
        // given - an SVG dashboard asset (matches the real favicon.svg)
        HttpResponse response = renderDashboardResource("/favicon-fixture.svg");

        // then - the asset was found and served
        assertThat(response.getBodyAsString(), is(notNullValue()));

        // and - the Content-Type is a valid, non-null SVG media type (it was null before the fix)
        String contentType = response.getFirstHeader(CONTENT_TYPE.toString());
        assertThat(contentType, is(notNullValue()));
        assertThat(contentType, equalTo("image/svg+xml"));
    }

    /**
     * Regression guard for the #2358 null-Content-Type NPE class extended to the DEFAULT_MIME_TYPE
     * fallback: an asset whose extension has no entry in the MIME map must still be served with a
     * valid, non-null {@code Content-Type}. {@code MIME_MAP.getOrDefault(extension, DEFAULT_MIME_TYPE)}
     * returns {@code application/octet-stream} for any unmapped extension; a regression that dropped
     * the fallback (returning null, as the pre-#2358 {@code MIME_MAP.get(...)} did) would crash
     * Netty's header encoder when the response was written. Every mapped extension (js, svg, ...) is
     * exercised elsewhere, so this is the only test that drives the fallback branch. {@code .webp} is
     * deliberately absent from MIME_MAP and from IS_STRING_CONTENT, so it takes the binary path too.
     */
    @Test
    public void servesUnmappedExtensionAssetWithDefaultOctetStreamContentType() throws Exception {
        // given - a dashboard asset whose extension (.webp) is not in the MIME map
        HttpResponse response = renderDashboardResource("/unmapped-fixture.webp");

        // then - the asset was found and served, not the 404 notFoundResponse
        assertThat(response.getBodyAsString(), is(notNullValue()));
        assertThat(response.getStatusCode(), is(200));

        // and - the Content-Type falls back to the non-null DEFAULT_MIME_TYPE (application/octet-stream)
        String contentType = response.getFirstHeader(CONTENT_TYPE.toString());
        assertThat(contentType, is(notNullValue()));
        assertThat(contentType, equalTo("application/octet-stream"));
    }

    private HttpResponse renderDashboardResource(String resourceSuffix) throws Exception {
        ChannelHandlerContext ctx = mock(ChannelHandlerContext.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);

        new DashboardHandler().renderDashboard(
            ctx,
            request().withMethod("GET").withPath(PATH_PREFIX + "/dashboard" + resourceSuffix).withKeepAlive(false)
        );

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(ctx).writeAndFlush(captor.capture());
        assertThat(captor.getValue(), is(instanceOf(HttpResponse.class)));
        return (HttpResponse) captor.getValue();
    }
}
