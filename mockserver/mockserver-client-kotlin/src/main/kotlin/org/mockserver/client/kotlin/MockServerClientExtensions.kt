package org.mockserver.client.kotlin

import org.mockserver.client.MockServerClient
import org.mockserver.mock.Expectation
import org.mockserver.model.HttpRequest
import org.mockserver.verify.VerificationTimes

/**
 * Creates (or, with an `id`, replaces) an expectation and returns what the server stored.
 *
 * ```
 * mockServer.expect {
 *     request { method = "GET"; path = "/x" } respond { status = 200; body = "ok" }
 * }
 * ```
 */
public fun MockServerClient.expect(block: ExpectationSpec.() -> Unit): Array<Expectation> =
    upsert(expectation(block))

/**
 * Verifies that requests matching [block] were received [times] times, at least once by default.
 *
 * @throws AssertionError if the verification fails
 */
public fun MockServerClient.verify(
    times: VerificationTimes = VerificationTimes.atLeast(1),
    block: RequestSpec.() -> Unit,
): MockServerClient = verify(request(block), times)

/**
 * Verifies that requests matching each `request { }` were received in that order.
 *
 * @throws AssertionError if the verification fails
 */
public fun MockServerClient.verifySequence(block: RequestSequenceSpec.() -> Unit): MockServerClient {
    val requests = RequestSequenceSpec().apply(block).requests
    require(requests.isNotEmpty()) { "verifySequence { } needs at least one request { }" }
    return verify(*requests.toTypedArray())
}

/** Clears the expectations and recorded requests that match [block]. */
public fun MockServerClient.clear(block: RequestSpec.() -> Unit): MockServerClient = clear(request(block))

/** Returns the recorded requests that match [block]. */
public fun MockServerClient.retrieveRecordedRequests(block: RequestSpec.() -> Unit): Array<HttpRequest> =
    retrieveRecordedRequests(request(block))

/** Returns the active expectations whose request matches [block]. */
public fun MockServerClient.retrieveActiveExpectations(block: RequestSpec.() -> Unit): Array<Expectation> =
    retrieveActiveExpectations(request(block))

/** The ordered requests of a [verifySequence]. */
@MockServerDsl
public class RequestSequenceSpec internal constructor() {

    internal val requests: MutableList<HttpRequest> = mutableListOf()

    /** The next request in the sequence. */
    public fun request(block: RequestSpec.() -> Unit) {
        requests += RequestSpec().apply(block).build()
    }
}
