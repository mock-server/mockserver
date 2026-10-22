<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use GuzzleHttp\RequestOptions;
use MockServer\HttpRequest;
use MockServer\HttpResponse;
use MockServer\MockServerClient;
use PHPUnit\Framework\TestCase;

/**
 * Tests for control-plane bearer-token authentication and TLS/mTLS option
 * wiring. These are additive, backward-compatible features: a configured token
 * adds an Authorization header to every control-plane request, and configured
 * CA / client-certificate paths surface as Guzzle VERIFY / CERT / SSL_KEY
 * request options.
 *
 * The transport is mocked with Guzzle's MockHandler; a history middleware
 * captures both the outgoing PSR-7 requests and the per-request option arrays
 * (the latter carries the TLS settings, which never appear on the wire).
 */
class MockServerClientAuthTlsTest extends TestCase
{
    /**
     * @param array<Response> $responses
     * @param array<array> &$history
     */
    private function createClientWithMock(
        MockServerClient $client,
        array $responses,
        array &$history = [],
    ): MockServerClient {
        $mock = new MockHandler($responses);
        $handlerStack = HandlerStack::create($mock);
        $handlerStack->push(Middleware::history($history));

        $reflection = new \ReflectionClass($client);
        $prop = $reflection->getProperty('httpClient');
        $prop->setAccessible(true);
        $prop->setValue($client, new GuzzleClient([
            'handler' => $handlerStack,
            'http_errors' => false,
            'headers' => ['Content-Type' => 'application/json; charset=utf-8'],
        ]));

        return $client;
    }

    public function testControlPlaneRequestCarriesBearerToken(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            controlPlaneBearerToken: 'my-secret-token',
        );
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $request = $history[0]['request'];
        $this->assertSame('Bearer my-secret-token', $request->getHeaderLine('Authorization'));
        // The default Content-Type must still be present alongside Authorization.
        $this->assertStringContainsString('application/json', $request->getHeaderLine('Content-Type'));
    }

    public function testBearerTokenViaSetter(): void
    {
        $history = [];
        $client = new MockServerClient('localhost', 1080);
        $client->withControlPlaneBearerToken('setter-token');
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $request = $history[0]['request'];
        $this->assertSame('Bearer setter-token', $request->getHeaderLine('Authorization'));
    }

    public function testControlPlaneRequestCarriesBasicCredentials(): void
    {
        $history = [];
        $client = new MockServerClient('localhost', 1080);
        $client->withControlPlaneBasicAuthentication('admin', 'pä:ss');
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $this->assertSame('Basic ' . base64_encode('admin:pä:ss'), $history[0]['request']->getHeaderLine('Authorization'));
    }

    public function testBearerTokenWinsOverBasicCredentials(): void
    {
        $history = [];
        $client = new MockServerClient('localhost', 1080, controlPlaneBearerToken: 'jwt');
        $client->withControlPlaneBasicAuthentication('admin', 's3cret');
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $this->assertSame('Bearer jwt', $history[0]['request']->getHeaderLine('Authorization'));
    }

    public function testBasicCredentialsWarnWithoutTlsAndStayOutOfErrors(): void
    {
        $log = tempnam(sys_get_temp_dir(), 'mockserver-basic');
        $previous = ini_set('error_log', $log);
        try {
            $history = [];
            $client = new MockServerClient('localhost', 1080);
            $client->withControlPlaneBasicAuthentication('admin', 'pa55-near-miss');
            $this->createClientWithMock($client, [new Response(500, [], 'failed')], $history);

            try {
                $client->reset();
                $this->fail('a 500 should fail the request');
            } catch (\Throwable $failure) {
                $this->assertStringNotContainsString('pa55-near-miss', (string) $failure);
                $this->assertStringNotContainsString(base64_encode('admin:pa55-near-miss'), (string) $failure);
            }
            $logged = (string) file_get_contents($log);
            $this->assertStringContainsString('without TLS', $logged);
            $this->assertStringNotContainsString('pa55-near-miss', $logged);
        } finally {
            ini_set('error_log', $previous === false ? '' : $previous);
            @unlink($log);
        }
    }

    public function testBasicCredentialsRefuseAUsernameThatCannotBeSent(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        (new MockServerClient('localhost', 1080))->withControlPlaneBasicAuthentication('a:b', 'x');
    }

    public function testBearerTokenSupplierIsEvaluatedPerRequest(): void
    {
        $history = [];
        $tokens = ['token-1', 'token-2'];
        $i = 0;
        $client = new MockServerClient(
            'localhost',
            1080,
            controlPlaneBearerToken: function () use (&$tokens, &$i): string {
                return $tokens[$i++];
            },
        );
        $this->createClientWithMock(
            $client,
            [new Response(200, [], ''), new Response(200, [], '')],
            $history,
        );

        $client->reset();
        $client->reset();

        $this->assertSame('Bearer token-1', $history[0]['request']->getHeaderLine('Authorization'));
        $this->assertSame('Bearer token-2', $history[1]['request']->getHeaderLine('Authorization'));
    }

    public function testBearerTokenAppliesToGetAndDeleteControlPlaneCalls(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            controlPlaneBearerToken: 'sre-token',
        );
        $this->createClientWithMock(
            $client,
            [
                new Response(200, [], json_encode(['state' => 'inactive'])),
                new Response(200, [], json_encode(['state' => 'inactive'])),
            ],
            $history,
        );

        // preemptionStatus() issues a GET, clearPreemption() issues a DELETE.
        $client->preemptionStatus();
        $client->clearPreemption();

        $this->assertSame('GET', $history[0]['request']->getMethod());
        $this->assertSame('Bearer sre-token', $history[0]['request']->getHeaderLine('Authorization'));
        $this->assertSame('DELETE', $history[1]['request']->getMethod());
        $this->assertSame('Bearer sre-token', $history[1]['request']->getHeaderLine('Authorization'));
    }

    public function testNoAuthorizationHeaderWhenTokenNotConfigured(): void
    {
        $history = [];
        $client = new MockServerClient('localhost', 1080);
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $this->assertFalse($history[0]['request']->hasHeader('Authorization'));
    }

    public function testEmptyTokenStringProducesNoAuthorizationHeader(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            controlPlaneBearerToken: '',
        );
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $this->assertFalse($history[0]['request']->hasHeader('Authorization'));
    }

    public function testBearerTokenPreservedWithContentTypeOverride(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            controlPlaneBearerToken: 'grpc-token',
        );
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        // uploadGrpcDescriptor overrides Content-Type to application/octet-stream.
        $client->uploadGrpcDescriptor("\x00\x01\x02");

        $request = $history[0]['request'];
        $this->assertSame('Bearer grpc-token', $request->getHeaderLine('Authorization'));
        $this->assertSame('application/octet-stream', $request->getHeaderLine('Content-Type'));
    }

    public function testCaCertPathAppliedAsVerifyOption(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            secure: true,
            caCertPath: '/etc/ssl/mockserver-ca.pem',
        );
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $options = $history[0]['options'];
        $this->assertSame('/etc/ssl/mockserver-ca.pem', $options[RequestOptions::VERIFY]);
    }

    public function testClientCertAndKeyAppliedAsCertAndSslKeyOptions(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            secure: true,
            clientCertPath: '/etc/ssl/client-cert.pem',
            clientKeyPath: '/etc/ssl/client-key.pem',
        );
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $options = $history[0]['options'];
        $this->assertSame('/etc/ssl/client-cert.pem', $options[RequestOptions::CERT]);
        $this->assertSame('/etc/ssl/client-key.pem', $options[RequestOptions::SSL_KEY]);
    }

    public function testTlsOptionsAppliedViaSetters(): void
    {
        $history = [];
        $client = new MockServerClient('localhost', 1080, secure: true);
        $client
            ->withCaCertificate('/ca.pem')
            ->withClientCertificate('/cert.pem', '/key.pem');
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $options = $history[0]['options'];
        $this->assertSame('/ca.pem', $options[RequestOptions::VERIFY]);
        $this->assertSame('/cert.pem', $options[RequestOptions::CERT]);
        $this->assertSame('/key.pem', $options[RequestOptions::SSL_KEY]);
    }

    public function testClientCertWithoutSeparateKeyOmitsSslKeyOption(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            secure: true,
            clientCertPath: '/etc/ssl/combined.pem',
        );
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $options = $history[0]['options'];
        $this->assertSame('/etc/ssl/combined.pem', $options[RequestOptions::CERT]);
        $this->assertArrayNotHasKey(RequestOptions::SSL_KEY, $options);
    }

    public function testTlsOptionsAppliedToGetControlPlaneCalls(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            secure: true,
            caCertPath: '/ca.pem',
        );
        $this->createClientWithMock(
            $client,
            [new Response(200, [], json_encode(['state' => 'inactive']))],
            $history,
        );

        // preemptionStatus() goes through the body-less send() path.
        $client->preemptionStatus();

        $options = $history[0]['options'];
        $this->assertSame('GET', $history[0]['request']->getMethod());
        $this->assertSame('/ca.pem', $options[RequestOptions::VERIFY]);
    }

    public function testNoTlsOptionsWhenNotConfigured(): void
    {
        $history = [];
        $client = new MockServerClient('localhost', 1080);
        $this->createClientWithMock($client, [new Response(200, [], '')], $history);

        $client->reset();

        $options = $history[0]['options'];
        // Guzzle always records its own secure-by-default verify=true in the
        // per-request options; the client must not override it with a custom CA
        // path, and must add no client certificate or key.
        $this->assertSame(true, $options[RequestOptions::VERIFY] ?? true);
        $this->assertArrayNotHasKey(RequestOptions::CERT, $options);
        $this->assertArrayNotHasKey(RequestOptions::SSL_KEY, $options);
    }

    public function testFullAuthAndMtlsCombined(): void
    {
        $history = [];
        $client = new MockServerClient(
            'localhost',
            1080,
            secure: true,
            controlPlaneBearerToken: 'combined-token',
            caCertPath: '/ca.pem',
            clientCertPath: '/cert.pem',
            clientKeyPath: '/key.pem',
        );
        $this->createClientWithMock(
            $client,
            [new Response(200, [], json_encode(['id' => 'abc']))],
            $history,
        );

        $client->when(
            HttpRequest::request()->method('GET')->path('/hello')
        )->respond(
            HttpResponse::response()->statusCode(200)->body('world')
        );

        $request = $history[0]['request'];
        $options = $history[0]['options'];
        $this->assertSame('PUT', $request->getMethod());
        $this->assertSame('/mockserver/expectation', $request->getUri()->getPath());
        $this->assertSame('Bearer combined-token', $request->getHeaderLine('Authorization'));
        $this->assertSame('/ca.pem', $options[RequestOptions::VERIFY]);
        $this->assertSame('/cert.pem', $options[RequestOptions::CERT]);
        $this->assertSame('/key.pem', $options[RequestOptions::SSL_KEY]);
    }
}
