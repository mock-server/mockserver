package org.mockserver.client.kotlin

import org.mockserver.matchers.MatchType
import org.mockserver.model.Body
import org.mockserver.model.HttpRequest
import org.mockserver.model.JsonBody
import org.mockserver.model.StringBody

/**
 * Marks the MockServer DSL scopes, so a nested block (such as `respond { }`) cannot silently call a
 * builder of the block around it.
 */
@DslMarker
public annotation class MockServerDsl

/**
 * Describes a request to match, for an expectation, a verification, a clear or a retrieve.
 *
 * Every property and function maps onto the Java `HttpRequest` builder; anything the DSL does not
 * cover is reachable through [customize].
 */
@MockServerDsl
public class RequestSpec internal constructor() {

    /** The HTTP method, such as `"GET"`; a leading `!` negates it. */
    public var method: String? = null

    /** The path, such as `"/orders/.*"`; regular expressions are supported. */
    public var path: String? = null

    /** Match only TLS (`true`) or only plain-text (`false`) requests. */
    public var secure: Boolean? = null

    /** Match only keep-alive (`true`) or only non-keep-alive (`false`) requests. */
    public var keepAlive: Boolean? = null

    private var bodyMatcher: Body<*>? = null

    private val customizers = mutableListOf<HttpRequest.() -> Unit>()

    /** An exact string body to match; setting it replaces any body set by [json] or [body]. */
    public var body: String?
        get() = (bodyMatcher as? StringBody)?.value
        set(value) {
            bodyMatcher = value?.let { StringBody.exact(it) }
        }

    /** Match a header with any one of [values]; values may be regular expressions. */
    public fun header(name: String, vararg values: String) {
        customizers += { withHeader(name, *values) }
    }

    /** Match a query string parameter with any one of [values]. */
    public fun queryParameter(name: String, vararg values: String) {
        customizers += { withQueryStringParameter(name, *values) }
    }

    /** Match a path parameter, such as `id` in a path of `/orders/{id}`. */
    public fun pathParameter(name: String, vararg values: String) {
        customizers += { withPathParameter(name, *values) }
    }

    /** Match a cookie. */
    public fun cookie(name: String, value: String) {
        customizers += { withCookie(name, value) }
    }

    /** Match a JSON body; by default fields in the request that are absent from [json] are ignored. */
    public fun json(json: String, matchType: MatchType = MatchType.ONLY_MATCHING_FIELDS) {
        bodyMatcher = JsonBody.json(json, matchType)
    }

    /** Match the body with any Java body matcher, such as `regex(...)`, `xpath(...)` or `jsonPath(...)`. */
    public fun body(matcher: Body<*>) {
        bodyMatcher = matcher
    }

    /** Apply any Java `HttpRequest` builder method the DSL does not cover; runs after the DSL's own settings. */
    public fun customize(block: HttpRequest.() -> Unit) {
        customizers += block
    }

    internal fun build(): HttpRequest {
        val request = HttpRequest.request()
        method?.let { request.withMethod(it) }
        path?.let { request.withPath(it) }
        secure?.let { request.withSecure(it) }
        keepAlive?.let { request.withKeepAlive(it) }
        bodyMatcher?.let { request.withBody(it) }
        customizers.forEach { request.it() }
        return request
    }
}

/**
 * Builds a Java `HttpRequest`, for use with any `MockServerClient` method that takes one:
 *
 * ```
 * mockServer.verify(request { method = "GET"; path = "/x" }, VerificationTimes.once())
 * ```
 */
public fun request(block: RequestSpec.() -> Unit): HttpRequest = RequestSpec().apply(block).build()
