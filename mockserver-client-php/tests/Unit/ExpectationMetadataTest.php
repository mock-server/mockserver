<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use MockServer\Exception\MockServerException;
use MockServer\Expectation;
use MockServer\ExpectationFilter;
use MockServer\HttpRequest;
use MockServer\HttpResponse;
use MockServer\MockServerClient;
use MockServer\Tests\Support\SharedFixtures;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

/**
 * Expectation metadata, retrieve / clear by metadata or unmatched, and paging.
 */
final class ExpectationMetadataTest extends TestCase
{
    private const JSON_FLAGS = JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION;

    /**
     * @param list<Response> $responses
     * @param array<int, array<string, mixed>> $history
     */
    private function client(array $responses, array &$history): MockServerClient
    {
        $handlerStack = HandlerStack::create(new MockHandler($responses));
        $handlerStack->push(Middleware::history($history));
        $client = new MockServerClient('localhost', 1080);
        $prop = (new \ReflectionClass($client))->getProperty('httpClient');
        $prop->setValue($client, new GuzzleClient([
            'handler' => $handlerStack,
            'http_errors' => false,
            'headers' => ['Content-Type' => 'application/json; charset=utf-8'],
        ]));
        return $client;
    }

    /**
     * @param array<int, array<string, mixed>> $history
     */
    private static function sent(array $history, int $index = 0): \Psr\Http\Message\RequestInterface
    {
        return $history[$index]['request'];
    }

    // ------------------------------------------------------------------
    // Model
    // ------------------------------------------------------------------

    public function testSharedFixtureRoundTripsExactly(): void
    {
        $path = __DIR__ . '/../../../test-fixtures/expectations/top_metadata.json';
        $fixture = SharedFixtures::expectation('top_metadata.json');
        $this->assertArrayHasKey('metadata', $fixture);

        $reencoded = json_encode(Expectation::fromArray($fixture), self::JSON_FLAGS);

        $this->assertSame(
            json_encode(json_decode((string) file_get_contents($path), false), self::JSON_FLAGS),
            $reencoded,
        );
        $this->assertStringContainsString('"note":null', $reencoded);
        $this->assertStringContainsString('"ticket":4211', $reencoded);
        $this->assertStringContainsString('"ratio":0.5', $reencoded);
        $this->assertStringContainsString('"flaky":false', $reencoded);
    }

    public function testTypedBuilderSerialisesFixtureMetadata(): void
    {
        $fixture = SharedFixtures::expectation('top_metadata.json');

        $expectation = (new Expectation())
            ->id('metadata-1')
            ->httpRequest(HttpRequest::request()->path('/metadata'))
            ->httpResponse(HttpResponse::response()->body('ok'))
            ->metadata($fixture['metadata']);

        $this->assertSame(
            json_encode($fixture['metadata'], self::JSON_FLAGS),
            json_encode($expectation->toArray()['metadata'], self::JSON_FLAGS),
        );
        $this->assertSame($fixture['metadata'], $expectation->getMetadata());
    }

    public function testTypedBuilderOmitsEmptyMetadata(): void
    {
        $expectation = (new Expectation())->httpRequest(HttpRequest::request()->path('/a'));

        $this->assertArrayNotHasKey('metadata', $expectation->toArray());
        $this->assertSame([], $expectation->getMetadata());
    }

    public function testAddMetadataKeepsNullAndEncodesNestedEmptyObject(): void
    {
        $expectation = (new Expectation())
            ->addMetadata('owner', 'team-a')
            ->addMetadata('note', null)
            ->addMetadata('empty', new \stdClass())
            ->addMetadata('list', []);

        $this->assertSame(
            '{"metadata":{"owner":"team-a","note":null,"empty":{},"list":[]}}',
            json_encode($expectation, self::JSON_FLAGS),
        );
    }

    public function testListLikeMetadataStillEncodesAsJsonObject(): void
    {
        $this->assertSame(
            '{"metadata":{"0":"a","1":"b"}}',
            json_encode((new Expectation())->metadata(['a', 'b']), self::JSON_FLAGS),
        );
        $decoded = json_decode('{"metadata":{"0":"a"}}', true);
        $this->assertSame('{"metadata":{"0":"a"}}', json_encode(Expectation::fromArray($decoded), self::JSON_FLAGS));
    }

    public function testFromArrayEncodesEmptyMetadataAsJsonObject(): void
    {
        $decoded = json_decode('{"httpRequest":{"path":"/a"},"metadata":{}}', true);

        $this->assertSame(
            '{"httpRequest":{"path":"/a"},"metadata":{}}',
            json_encode(Expectation::fromArray($decoded), self::JSON_FLAGS),
        );
    }

    public function testWhenWithMetadataSendsMetadataObject(): void
    {
        $history = [];
        $client = $this->client([new Response(201, [], '[]')], $history);

        $client->when(HttpRequest::request()->path('/a'))
            ->withMetadata(['owner' => 'team-a', 'tags' => ['smoke'], 'note' => null])
            ->respond(HttpResponse::response()->statusCode(200));

        $this->assertStringContainsString(
            '"metadata":{"owner":"team-a","tags":["smoke"],"note":null}',
            (string) self::sent($history)->getBody(),
        );
    }

    // ------------------------------------------------------------------
    // Wire: retrieve
    // ------------------------------------------------------------------

    /**
     * @return array<string, array{mixed, string}>
     */
    public static function metadataValues(): array
    {
        return [
            'leading not marker' => ['!team-a', '%21team-a'],
            'leading optional marker' => ['?x', '%3Fx'],
            'percent' => ['100%', '100%25'],
            'plus' => ['a+b', 'a%2Bb'],
            'space' => ['a b', 'a%20b'],
            'ampersand hash equals' => ['a&b=c#d', 'a%26b%3Dc%23d'],
            'number' => [42, '42'],
            'float' => [0.5, '0.5'],
            'boolean' => [true, 'true'],
            'list' => [['a', 'b'], '%5B%22a%22%2C%22b%22%5D'],
            'object' => [['k' => 'v/w'], '%7B%22k%22%3A%22v%2Fw%22%7D'],
            'string that looks like a number' => ['42', '42'],
        ];
    }

    #[DataProvider('metadataValues')]
    public function testRetrieveByMetadataSendsValueLiterally(mixed $value, string $encoded): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveActiveExpectationsByMetadata('owner', $value);

        $request = self::sent($history);
        $this->assertSame('PUT', $request->getMethod());
        $this->assertSame('/mockserver/retrieve', $request->getUri()->getPath());
        $this->assertSame(
            'type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner&metadataValue=' . $encoded,
            $request->getUri()->getQuery(),
        );
        $this->assertSame('', (string) $request->getBody());
    }

    public function testRetrieveByMetadataKeySendsNoValue(): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveActiveExpectationsByMetadataKey('!owner key');

        $this->assertSame(
            'type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=%21owner%20key',
            self::sent($history)->getUri()->getQuery(),
        );
    }

    public function testWithMetadataKeyDropsAnEarlierValue(): void
    {
        $filter = ExpectationFilter::expectationFilter()->withMetadata('owner', 'team-a')->withMetadataKey('owner');

        $this->assertNull($filter->getMetadataValue());
        $this->assertSame(['metadataKey' => 'owner'], $filter->toQueryParameters());
    }

    public function testRetrieveUnmatchedSendsUnmatchedTrue(): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveUnmatchedExpectations();

        $this->assertSame(
            'type=ACTIVE_EXPECTATIONS&format=JSON&unmatched=true',
            self::sent($history)->getUri()->getQuery(),
        );
    }

    public function testRetrieveWithFilterSendsEveryParameterAndRequestBody(): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveActiveExpectationsWithFilter(
            HttpRequest::request()->path('/api'),
            ExpectationFilter::expectationFilter()
                ->withMetadata('owner', 'team-a')
                ->withMetadataJsonPath("$[?(@.owner == 'team-a')]")
                ->withUnmatched(true)
                ->withLimit(10)
                ->withOffset(0),
        );

        $request = self::sent($history);
        $this->assertSame(
            'type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner&metadataValue=team-a'
            . '&metadataJsonPath=%24%5B%3F%28%40.owner%20%3D%3D%20%27team-a%27%29%5D'
            . '&unmatched=true&limit=10&offset=0',
            $request->getUri()->getQuery(),
        );
        $this->assertSame(['path' => '/api'], json_decode((string) $request->getBody(), true));
    }

    public function testRetrieveWithEmptyFilterSendsOnlyTypeAndFormat(): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveActiveExpectationsWithFilter(null, ExpectationFilter::expectationFilter());

        $this->assertSame('type=ACTIVE_EXPECTATIONS&format=JSON', self::sent($history)->getUri()->getQuery());
    }

    public function testRetrieveRecordedExpectationsPageSendsOffsetAndLimit(): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveRecordedExpectationsPage(HttpRequest::request()->path('/api'), 20, 5);

        $request = self::sent($history);
        $this->assertSame('type=RECORDED_EXPECTATIONS&format=JSON&limit=5&offset=20', $request->getUri()->getQuery());
        $this->assertSame(['path' => '/api'], json_decode((string) $request->getBody(), true));
    }

    public function testRetrieveWithFilterPreservesMetadataJsonShape(): void
    {
        $json = '[{"id":"a","httpRequest":{"path":"/a"},"metadata":{"empty":{},"nested":{"inner":{}},'
            . '"numericKeys":{"0":"x"},"list":[],"note":null,"n":1.5}},{"id":"b","httpRequest":{}}]';
        $history = [];
        $client = $this->client([new Response(200, [], $json)], $history);

        $result = $client->retrieveActiveExpectationsByMetadataKey('note');

        $this->assertCount(2, $result);
        $this->assertSame('a', $result[0]['id']);
        $this->assertSame([], $result[1]['httpRequest'], 'non-metadata fields decode as json_decode(..., true)');
        $this->assertSame(
            json_encode(json_decode($json, false)[0], self::JSON_FLAGS),
            json_encode(Expectation::fromArray($result[0]), self::JSON_FLAGS),
        );
    }

    public function testRetrieveWithFilterThrowsOnServerError(): void
    {
        $history = [];
        $client = $this->client([new Response(400, [], 'bad JSONPath')], $history);

        $this->expectException(MockServerException::class);
        $this->expectExceptionMessage('bad JSONPath');
        $client->retrieveActiveExpectationsWithFilter(
            null,
            ExpectationFilter::expectationFilter()->withMetadataJsonPath('$[bad'),
        );
    }

    // ------------------------------------------------------------------
    // Wire: clear
    // ------------------------------------------------------------------

    public function testClearByMetadataSendsTypeExpectationsAndValue(): void
    {
        $history = [];
        $client = $this->client([new Response(200)], $history);

        $client->clearByMetadata('owner', '!team-a');

        $request = self::sent($history);
        $this->assertSame('PUT', $request->getMethod());
        $this->assertSame('/mockserver/clear', $request->getUri()->getPath());
        $this->assertSame('type=expectations&metadataKey=owner&metadataValue=%21team-a', $request->getUri()->getQuery());
        $this->assertSame('', (string) $request->getBody());
    }

    public function testClearByMetadataKeySendsNoValue(): void
    {
        $history = [];
        $client = $this->client([new Response(200)], $history);

        $client->clearByMetadataKey('owner');

        $this->assertSame('type=expectations&metadataKey=owner', self::sent($history)->getUri()->getQuery());
    }

    public function testClearUnmatchedSendsUnmatchedTrue(): void
    {
        $history = [];
        $client = $this->client([new Response(200)], $history);

        $client->clearUnmatchedExpectations();

        $this->assertSame('type=expectations&unmatched=true', self::sent($history)->getUri()->getQuery());
    }

    public function testClearWithFilterSendsJsonPathAndRequestBody(): void
    {
        $history = [];
        $client = $this->client([new Response(200)], $history);

        $client->clearWithFilter(
            HttpRequest::request()->path('/api'),
            ExpectationFilter::expectationFilter()->withMetadataJsonPath('$.tags[?(@ == "a+b")]'),
        );

        $request = self::sent($history);
        $this->assertSame(
            'type=expectations&metadataJsonPath=%24.tags%5B%3F%28%40%20%3D%3D%20%22a%2Bb%22%29%5D',
            $request->getUri()->getQuery(),
        );
        $this->assertSame(['path' => '/api'], json_decode((string) $request->getBody(), true));
    }

    public function testClearWithFilterThrowsOnServerError(): void
    {
        $history = [];
        $client = $this->client([new Response(400, [], 'invalid filter')], $history);

        $this->expectException(MockServerException::class);
        $client->clearByMetadataKey('owner');
    }

    // ------------------------------------------------------------------
    // Fail-closed rejections: each throws and sends nothing
    // ------------------------------------------------------------------

    /**
     * @return array<string, array{callable(MockServerClient): mixed}>
     */
    public static function rejectedCalls(): array
    {
        return [
            'retrieve blank key' => [static fn(MockServerClient $c) => $c->retrieveActiveExpectationsByMetadata('', 'v')],
            'retrieve whitespace key' => [static fn(MockServerClient $c) => $c->retrieveActiveExpectationsByMetadataKey('  ')],
            'retrieve null value' => [static fn(MockServerClient $c) => $c->retrieveActiveExpectationsByMetadata('owner', null)],
            'retrieve blank JSONPath' => [static fn(MockServerClient $c) => $c->retrieveActiveExpectationsWithFilter(
                null,
                ExpectationFilter::expectationFilter()->withMetadataJsonPath(' '),
            )],
            'retrieve negative limit' => [static fn(MockServerClient $c) => $c->retrieveActiveExpectationsWithFilter(
                null,
                ExpectationFilter::expectationFilter()->withLimit(-1),
            )],
            'retrieve negative offset' => [static fn(MockServerClient $c) => $c->retrieveActiveExpectationsWithFilter(
                null,
                ExpectationFilter::expectationFilter()->withOffset(-1),
            )],
            'recorded negative offset' => [static fn(MockServerClient $c) => $c->retrieveRecordedExpectationsPage(null, -1, 5)],
            'recorded negative limit' => [static fn(MockServerClient $c) => $c->retrieveRecordedExpectationsPage(null, 0, -5)],
            'clear blank key' => [static fn(MockServerClient $c) => $c->clearByMetadata('', 'v')],
            'clear blank key only' => [static fn(MockServerClient $c) => $c->clearByMetadataKey("\t")],
            'clear null value' => [static fn(MockServerClient $c) => $c->clearByMetadata('owner', null)],
            'clear blank JSONPath' => [static fn(MockServerClient $c) => $c->clearWithFilter(
                null,
                ExpectationFilter::expectationFilter()->withMetadataJsonPath(''),
            )],
            'clear with no selection' => [static fn(MockServerClient $c) => $c->clearWithFilter(
                null,
                ExpectationFilter::expectationFilter(),
            )],
            'clear with unmatched false only' => [static fn(MockServerClient $c) => $c->clearWithFilter(
                HttpRequest::request()->path('/a'),
                ExpectationFilter::expectationFilter()->withUnmatched(false),
            )],
            'clear with limit' => [static fn(MockServerClient $c) => $c->clearWithFilter(
                null,
                ExpectationFilter::expectationFilter()->withMetadataKey('owner')->withLimit(1),
            )],
            'clear with offset' => [static fn(MockServerClient $c) => $c->clearWithFilter(
                null,
                ExpectationFilter::expectationFilter()->withUnmatched(true)->withOffset(0),
            )],
        ];
    }

    #[DataProvider('rejectedCalls')]
    public function testRejectedCallThrowsAndSendsNothing(callable $call): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        try {
            $call($client);
            $this->fail('expected InvalidArgumentException');
        } catch (\InvalidArgumentException $e) {
            $this->assertNotSame('', $e->getMessage());
        }
        $this->assertSame([], $history, 'nothing must be sent for an invalid filter');
    }
}
