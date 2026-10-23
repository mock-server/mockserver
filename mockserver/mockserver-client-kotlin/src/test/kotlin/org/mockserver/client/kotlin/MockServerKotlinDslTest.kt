package org.mockserver.client.kotlin

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.time.Duration as JavaDuration
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThanOrEqualTo
import org.hamcrest.Matchers.`is`
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.Times
import org.mockserver.model.RegexBody.regex
import org.mockserver.verify.VerificationTimes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MockServerKotlinDslTest {

    private lateinit var mockServer: ClientAndServer
    private val httpClient: HttpClient = HttpClient.newBuilder().connectTimeout(JavaDuration.ofSeconds(10)).build()

    @BeforeAll
    fun startServer() {
        mockServer = ClientAndServer.startClientAndServer()
    }

    @AfterAll
    fun stopServer() {
        mockServer.stop()
    }

    @BeforeEach
    fun reset() {
        mockServer.reset()
    }

    private fun send(
        method: String,
        path: String,
        body: String? = null,
        headers: Map<String, String> = emptyMap(),
    ): java.net.http.HttpResponse<String> {
        val builder = java.net.http.HttpRequest.newBuilder(URI.create("http://localhost:${mockServer.port}$path"))
            .timeout(JavaDuration.ofSeconds(10))
            .method(method, body?.let { BodyPublishers.ofString(it) } ?: BodyPublishers.noBody())
        headers.forEach { (name, value) -> builder.header(name, value) }
        return httpClient.send(builder.build(), BodyHandlers.ofString())
    }

    @Test
    fun `infix expectation responds`() {
        mockServer.expect { request { method = "GET"; path = "/x" } respond { status = 200; body = "ok" } }

        val response = send("GET", "/x")

        assertThat(response.statusCode(), `is`(200))
        assertThat(response.body(), `is`("ok"))
        assertThat(send("POST", "/x", "").statusCode(), `is`(404))
    }

    @Test
    fun `block expectation matches headers, query and json body and returns headers, cookie and json`() {
        val created = mockServer.expect {
            id = "create-order"
            priority = 10
            request {
                method = "POST"
                path = "/orders"
                header("X-Tenant", "acme")
                queryParameter("dryRun", "false")
                json("""{ "sku": "abc" }""")
            }
            respond {
                status = 201
                reason = "Created"
                header("Location", "/orders/1")
                cookie("session", "s1")
                json("""{ "id": 1 }""")
            }
        }

        assertThat(created.single().id, `is`("create-order"))

        val response = send(
            "POST",
            "/orders?dryRun=false",
            """{ "sku": "abc", "quantity": 2 }""",
            mapOf("X-Tenant" to "acme", "Content-Type" to "application/json"),
        )
        assertThat(response.statusCode(), `is`(201))
        assertThat(response.headers().firstValue("Location").orElse(null), `is`("/orders/1"))
        assertThat(response.headers().firstValue("Set-Cookie").orElse(""), containsString("session=s1"))
        assertThat(response.headers().firstValue("Content-Type").orElse(""), containsString("application/json"))
        assertThat(response.body(), containsString("\"id\""))

        val wrongTenant = send(
            "POST",
            "/orders?dryRun=false",
            """{ "sku": "abc" }""",
            mapOf("X-Tenant" to "other", "Content-Type" to "application/json"),
        )
        assertThat(wrongTenant.statusCode(), `is`(404))
    }

    @Test
    fun `times limits how often an expectation matches and a delay is applied`() {
        mockServer.expect {
            times = Times.once()
            timeToLive = 5.minutes
            request { path = "/once" }
            respond { body = "first"; delay = 200.milliseconds }
        }

        val startNanos = System.nanoTime()
        val first = send("GET", "/once")
        val elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000

        assertThat(first.body(), `is`("first"))
        assertThat(elapsedMillis, greaterThanOrEqualTo(200L))
        assertThat(send("GET", "/once").statusCode(), `is`(404))
    }

    @Test
    fun `body matcher and customize reach the Java builders`() {
        mockServer.expect {
            request {
                method = "PUT"
                path = "/items/.*"
                body(regex("name=.*"))
                customize { withHeader("X-Trace") }
            }
            respond {
                status = 202
                customize { withHeader("X-Custom", "yes") }
            }
        }

        val response = send("PUT", "/items/7", "name=widget", mapOf("X-Trace" to "t1"))

        assertThat(response.statusCode(), `is`(202))
        assertThat(response.headers().firstValue("X-Custom").orElse(null), `is`("yes"))
    }

    @Test
    fun `forward sends the request to another server`() {
        ClientAndServer.startClientAndServer().use { upstream ->
            upstream.expect { request { path = "/upstream" } respond { body = "from upstream" } }
            mockServer.expect {
                request { path = "/upstream" } forward { host = "localhost"; port = upstream.port }
            }

            val response = send("GET", "/upstream")

            assertThat(response.body(), `is`("from upstream"))
        }
    }

    @Test
    fun `error drops the connection`() {
        mockServer.expect { request { path = "/drop" } error { dropConnection = true } }

        assertThrows<java.io.IOException> { send("GET", "/drop") }
    }

    @Test
    fun `error resets the connection`() {
        mockServer.expect { request { path = "/reset" } error { resetConnection = true } }

        assertThrows<java.io.IOException> { send("GET", "/reset") }
    }

    @Test
    fun `verify checks counts and passes and fails as the requests dictate`() {
        mockServer.expect { request { path = "/v" } respond { status = 204 } }
        send("GET", "/v")
        send("GET", "/v")

        mockServer.verify { path = "/v" }
        mockServer.verify(VerificationTimes.exactly(2)) { method = "GET"; path = "/v" }
        mockServer.verify(VerificationTimes.never()) { path = "/never" }

        val failure = assertThrows<AssertionError> {
            mockServer.verify(VerificationTimes.exactly(3)) { path = "/v" }
        }
        assertThat(failure.message, containsString("/v"))
    }

    @Test
    fun `verifySequence checks order`() {
        send("GET", "/a")
        send("GET", "/b")

        mockServer.verifySequence {
            request { path = "/a" }
            request { path = "/b" }
        }
        assertThrows<AssertionError> {
            mockServer.verifySequence {
                request { path = "/b" }
                request { path = "/a" }
            }
        }
    }

    @Test
    fun `retrieve and clear take the request DSL`() {
        mockServer.expect { request { path = "/keep" } respond { body = "keep" } }
        mockServer.expect { request { path = "/drop" } respond { body = "drop" } }
        send("GET", "/keep")

        assertThat(mockServer.retrieveRecordedRequests { path = "/keep" }.size, `is`(1))
        assertThat(mockServer.retrieveActiveExpectations { path = "/drop" }.size, `is`(1))

        mockServer.clear { path = "/drop" }

        assertThat(mockServer.retrieveActiveExpectations { path = "/drop" }.size, `is`(0))
        assertThat(send("GET", "/keep").body(), `is`("keep"))
    }

    @Test
    fun `standalone builders work with the Java client API`() {
        mockServer.`when`(request { path = "/java" }).respond(response { status = 418 })
        mockServer.upsert(expectation { request { path = "/upserted" } respond { body = "upserted" } })

        assertThat(send("GET", "/java").statusCode(), `is`(418))
        assertThat(send("GET", "/upserted").body(), `is`("upserted"))
        mockServer.verify(request { path = "/java" }, VerificationTimes.once())
    }

    @Test
    fun `timeToLive maps infinite to unlimited and finite values to milliseconds`() {
        val forEver = expectation { timeToLive = Duration.INFINITE; request { path = "/ttl" } respond { } }
        val unset = expectation { request { path = "/ttl" } respond { } }
        val twoSeconds = expectation { timeToLive = 2.seconds; request { path = "/ttl" } respond { } }
        val oneMilli = expectation { timeToLive = 1.milliseconds; request { path = "/ttl" } respond { } }

        assertThat(forEver.timeToLive.isUnlimited, `is`(true))
        assertThat(unset.timeToLive.isUnlimited, `is`(true))
        assertThat(twoSeconds.timeToLive.isUnlimited, `is`(false))
        assertThat(twoSeconds.timeToLive.timeUnit, `is`(TimeUnit.MILLISECONDS))
        assertThat(twoSeconds.timeToLive.timeToLive, `is`(2000L))
        assertThat(oneMilli.timeToLive.timeToLive, `is`(1L))

        mockServer.upsert(forEver)
        assertThat(send("GET", "/ttl").statusCode(), `is`(200))
    }

    @Test
    fun `timeToLive and delay reject values they cannot represent`() {
        listOf(500.microseconds, Duration.ZERO, (-1).seconds, -Duration.INFINITE).forEach { invalid ->
            assertThrows<IllegalArgumentException>("timeToLive $invalid") {
                expectation { timeToLive = invalid; request { path = "/ttl" } respond { } }
            }
        }
        assertThrows<IllegalArgumentException> {
            expectation { request { path = "/d" } respond { delay = Duration.INFINITE } }
        }
        assertThrows<IllegalArgumentException> {
            expectation { request { path = "/d" } respond { delay = (-1).milliseconds } }
        }
    }

    @Test
    fun `invalid expectations are rejected before anything is sent`() {
        assertThrows<IllegalStateException> { expectation { request { path = "/no-action" } } }
        assertThrows<IllegalStateException> { expectation { respond { status = 200 } } }
        assertThrows<IllegalStateException> {
            expectation { request { path = "/two" } respond { status = 200 }; error { dropConnection = true } }
        }
        assertThrows<IllegalArgumentException> { expectation { request { path = "/f" } forward { port = 1 } } }
        assertThrows<IllegalArgumentException> { mockServer.verifySequence { } }
        assertThat(mockServer.retrieveActiveExpectations { }.size, equalTo(0))
    }
}
