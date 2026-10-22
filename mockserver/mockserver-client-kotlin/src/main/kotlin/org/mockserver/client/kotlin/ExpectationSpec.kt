package org.mockserver.client.kotlin

import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import org.mockserver.matchers.TimeToLive
import org.mockserver.matchers.Times
import org.mockserver.mock.Expectation

/**
 * Describes one expectation: the request to match, how often and for how long it applies, and the
 * single action to take (`respond`, `forward` or `error`).
 *
 * ```
 * mockServer.expect {
 *     request { method = "GET"; path = "/x" } respond { status = 200; body = "ok" }
 * }
 * ```
 */
@MockServerDsl
public class ExpectationSpec internal constructor() {

    /** How many times the expectation matches; unlimited by default. */
    public var times: Times = Times.unlimited()

    /**
     * How long the expectation stays active, to the millisecond; `null` (the default) or
     * `Duration.INFINITE` means for ever.
     */
    public var timeToLive: Duration? = null

    /** Higher priorities are matched first; 0 by default. */
    public var priority: Int = 0

    /** A fixed id, so a later expectation with the same id replaces this one. */
    public var id: String? = null

    private var requestSpec: RequestSpec? = null

    private var action: (Expectation.() -> Unit)? = null

    /** The request to match. */
    public fun request(block: RequestSpec.() -> Unit): RequestSpec {
        check(requestSpec == null) { "an expectation takes exactly one request { }" }
        return RequestSpec().apply(block).also { requestSpec = it }
    }

    /** Return a response. */
    public fun respond(block: ResponseSpec.() -> Unit) {
        setAction { thenRespond(ResponseSpec().apply(block).build()) }
    }

    /** Forward the request to another host. */
    public fun forward(block: ForwardSpec.() -> Unit) {
        setAction { thenForward(ForwardSpec().apply(block).build()) }
    }

    /** Produce a connection-level error instead of a response. */
    public fun error(block: ErrorSpec.() -> Unit) {
        setAction { thenError(ErrorSpec().apply(block).build()) }
    }

    /** Infix form of [respond], so the request and its action read as one line. */
    public infix fun RequestSpec.respond(block: ResponseSpec.() -> Unit) {
        this@ExpectationSpec.respond(block)
    }

    /** Infix form of [forward]. */
    public infix fun RequestSpec.forward(block: ForwardSpec.() -> Unit) {
        this@ExpectationSpec.forward(block)
    }

    /** Infix form of [error]. */
    public infix fun RequestSpec.error(block: ErrorSpec.() -> Unit) {
        this@ExpectationSpec.error(block)
    }

    private fun setAction(newAction: Expectation.() -> Unit) {
        check(action == null) { "an expectation takes exactly one action: respond { }, forward { } or error { }" }
        action = newAction
    }

    internal fun build(): Expectation {
        val request = checkNotNull(requestSpec) { "an expectation needs a request { }" }.build()
        val ttl = timeToLive.toTimeToLive()
        val expectation = Expectation(request, times, ttl, priority)
        id?.let { expectation.withId(it) }
        checkNotNull(action) { "an expectation needs an action: respond { }, forward { } or error { }" }(expectation)
        return expectation
    }
}

/** Builds a Java `Expectation` without sending it, for example to pass to `MockServerClient.upsert`. */
public fun expectation(block: ExpectationSpec.() -> Unit): Expectation = ExpectationSpec().apply(block).build()

internal fun Duration?.toTimeToLive(): TimeToLive {
    if (this == null || this == Duration.INFINITE) {
        return TimeToLive.unlimited()
    }
    // TimeToLive has millisecond granularity, so a shorter value would expire on creation
    require(this >= 1.milliseconds) { "timeToLive must be at least 1ms but was $this" }
    return TimeToLive.exactly(TimeUnit.MILLISECONDS, inWholeMilliseconds)
}
