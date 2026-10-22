package org.mockserver.client.kotlin

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import org.mockserver.model.BodyWithContentType
import org.mockserver.model.Delay
import org.mockserver.model.HttpError
import org.mockserver.model.HttpForward
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody

/** Describes the response MockServer returns when an expectation matches. */
@MockServerDsl
public class ResponseSpec internal constructor() {

    /** The status code; MockServer returns 200 when it is not set. */
    public var status: Int? = null

    /** The reason phrase; MockServer derives it from [status] when it is not set. */
    public var reason: String? = null

    /** How long MockServer waits before it sends the response. */
    public var delay: Duration? = null

    private var bodyValue: BodyWithContentType<*>? = null

    private var stringBody: String? = null

    private val customizers = mutableListOf<HttpResponse.() -> Unit>()

    /** A string body; setting it replaces any body set by [json] or [body]. */
    public var body: String?
        get() = stringBody
        set(value) {
            stringBody = value
            bodyValue = null
        }

    /** Add a header with one or more values. */
    public fun header(name: String, vararg values: String) {
        customizers += { withHeader(name, *values) }
    }

    /** Add a `Set-Cookie` header. */
    public fun cookie(name: String, value: String) {
        customizers += { withCookie(name, value) }
    }

    /** A JSON body, sent with `Content-Type: application/json`. */
    public fun json(json: String) {
        body(JsonBody.json(json))
    }

    /** Any Java body, such as `xml(...)` or `binary(...)`. */
    public fun body(body: BodyWithContentType<*>) {
        bodyValue = body
        stringBody = null
    }

    /** Apply any Java `HttpResponse` builder method the DSL does not cover; runs after the DSL's own settings. */
    public fun customize(block: HttpResponse.() -> Unit) {
        customizers += block
    }

    internal fun build(): HttpResponse {
        val response = HttpResponse.response()
        status?.let { response.withStatusCode(it) }
        reason?.let { response.withReasonPhrase(it) }
        stringBody?.let { response.withBody(it) }
        bodyValue?.let { response.withBody(it) }
        delay?.let { response.withDelay(it.toDelay()) }
        customizers.forEach { response.it() }
        return response
    }
}

/** Describes where MockServer forwards a matched request. */
@MockServerDsl
public class ForwardSpec internal constructor() {

    /** The host to forward to; required. */
    public var host: String? = null

    /** The port to forward to; MockServer uses 80 when it is not set. */
    public var port: Int? = null

    /** `HTTP` or `HTTPS`; MockServer uses `HTTP` when it is not set. */
    public var scheme: HttpForward.Scheme? = null

    /** How long MockServer waits before it forwards the request. */
    public var delay: Duration? = null

    internal fun build(): HttpForward {
        val forward = HttpForward.forward()
            .withHost(requireNotNull(host) { "forward { } needs a host" })
        port?.let { forward.withPort(it) }
        scheme?.let { forward.withScheme(it) }
        delay?.let { forward.withDelay(it.toDelay()) }
        return forward
    }
}

/** Describes a connection-level error MockServer produces instead of a response. */
@MockServerDsl
public class ErrorSpec internal constructor() {

    /** Close the connection. */
    public var dropConnection: Boolean? = null

    /** Raw bytes to write to the connection before any close, such as a malformed response. */
    public var responseBytes: ByteArray? = null

    /** How long MockServer waits before it produces the error. */
    public var delay: Duration? = null

    internal fun build(): HttpError {
        val error = HttpError.error()
        dropConnection?.let { error.withDropConnection(it) }
        responseBytes?.let { error.withResponseBytes(it) }
        delay?.let { error.withDelay(it.toDelay()) }
        return error
    }
}

/**
 * Builds a Java `HttpResponse`, for use with any `MockServerClient` method that takes one.
 */
public fun response(block: ResponseSpec.() -> Unit): HttpResponse = ResponseSpec().apply(block).build()

internal fun Duration.toDelay(): Delay {
    require(!isNegative() && isFinite()) { "delay must be finite and not negative but was $this" }
    return Delay(TimeUnit.MILLISECONDS, inWholeMilliseconds)
}
