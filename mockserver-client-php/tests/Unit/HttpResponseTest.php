<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use MockServer\ConnectionOptions;
use MockServer\Delay;
use MockServer\HttpResponse;
use MockServer\RecoverAfter;
use MockServer\Tests\Support\SharedFixtures;
use PHPUnit\Framework\TestCase;

class HttpResponseTest extends TestCase
{
    public function testEmptyResponse(): void
    {
        $response = HttpResponse::response();
        $this->assertSame([], $response->toArray());
    }

    public function testStatusCode(): void
    {
        $response = HttpResponse::response()->statusCode(201);

        $this->assertSame(['statusCode' => 201], $response->toArray());
    }

    public function testReasonPhrase(): void
    {
        $response = HttpResponse::response()
            ->statusCode(404)
            ->reasonPhrase('Not Found');

        $this->assertSame([
            'statusCode' => 404,
            'reasonPhrase' => 'Not Found',
        ], $response->toArray());
    }

    public function testHeaders(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->header('Content-Type', 'application/json')
            ->header('X-Custom', 'a', 'b');

        $expected = [
            'statusCode' => 200,
            'headers' => [
                'Content-Type' => ['application/json'],
                'X-Custom' => ['a', 'b'],
            ],
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testCookies(): void
    {
        $response = HttpResponse::response()
            ->cookie('session', 'xyz');

        $expected = [
            'cookies' => [
                'session' => ['xyz'],
            ],
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testStringBody(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->body('hello world');

        $expected = [
            'statusCode' => 200,
            'body' => 'hello world',
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testJsonBody(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->jsonBody(['message' => 'ok']);

        $array = $response->toArray();

        $this->assertSame(200, $array['statusCode']);
        $this->assertSame('JSON', $array['body']['type']);
        $this->assertSame('{"message":"ok"}', $array['body']['json']);
    }

    public function testDelay(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->delay(Delay::milliseconds(500));

        $expected = [
            'statusCode' => 200,
            'delay' => [
                'timeUnit' => 'MILLISECONDS',
                'value' => 500,
            ],
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testConnectionOptions(): void
    {
        $opts = (new ConnectionOptions())
            ->closeSocket(true)
            ->suppressContentLengthHeader(true);

        $response = HttpResponse::response()
            ->statusCode(200)
            ->connectionOptions($opts);

        $array = $response->toArray();

        $this->assertSame(200, $array['statusCode']);
        $this->assertTrue($array['connectionOptions']['closeSocket']);
        $this->assertTrue($array['connectionOptions']['suppressContentLengthHeader']);
    }

    public function testFileBody(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->fileBody('/path/to/file.html');

        $expected = [
            'statusCode' => 200,
            'body' => [
                'type' => 'FILE',
                'filePath' => '/path/to/file.html',
            ],
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testFileBodyWithContentType(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->fileBody('/path/to/data.json', 'application/json');

        $array = $response->toArray();

        $this->assertSame('FILE', $array['body']['type']);
        $this->assertSame('/path/to/data.json', $array['body']['filePath']);
        $this->assertSame('application/json', $array['body']['contentType']);
        $this->assertArrayNotHasKey('templateType', $array['body']);
    }

    public function testFileBodyWithTemplateType(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->fileBody('/templates/response.html', 'text/html', 'VELOCITY');

        $expected = [
            'statusCode' => 200,
            'body' => [
                'type' => 'FILE',
                'filePath' => '/templates/response.html',
                'contentType' => 'text/html',
                'templateType' => 'VELOCITY',
            ],
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testFileBodyWithMustacheTemplateType(): void
    {
        $response = HttpResponse::response()
            ->fileBody('/templates/response.mustache', null, 'MUSTACHE');

        $expected = [
            'body' => [
                'type' => 'FILE',
                'filePath' => '/templates/response.mustache',
                'templateType' => 'MUSTACHE',
            ],
        ];

        $this->assertSame($expected, $response->toArray());
    }

    public function testFileBodyWithTemplatedFilePath(): void
    {
        $response = HttpResponse::response()
            ->fileBody('responses/{{ request.pathParameters.name.0 }}.json', 'application/json', 'MUSTACHE', true);

        $expected = [
            'body' => [
                'type' => 'FILE',
                'filePath' => 'responses/{{ request.pathParameters.name.0 }}.json',
                'contentType' => 'application/json',
                'templateType' => 'MUSTACHE',
                'templatedFilePath' => true,
            ],
        ];

        $this->assertSame($expected, $response->toArray());
        $json = json_encode($response, JSON_THROW_ON_ERROR);
        $this->assertSame($expected, json_decode($json, true));
    }

    public function testFileBodyOmitsTemplatedFilePathWhenFalse(): void
    {
        $response = HttpResponse::response()
            ->fileBody('/templates/response.vm', null, 'VELOCITY', false);

        $this->assertArrayNotHasKey('templatedFilePath', $response->toArray()['body']);
    }

    public function testJsonSerialize(): void
    {
        $response = HttpResponse::response()
            ->statusCode(204);

        $json = json_encode($response, JSON_THROW_ON_ERROR);
        $decoded = json_decode($json, true);

        $this->assertSame(204, $decoded['statusCode']);
    }

    public function testGetters(): void
    {
        $response = HttpResponse::response()
            ->statusCode(200)
            ->body('test');

        $this->assertSame(200, $response->getStatusCode());
        $this->assertSame('test', $response->getBody());
        $this->assertSame([], $response->getHeaders());
        $this->assertNull($response->getDelay());
    }

    public function testPrimaryIsSerialised(): void
    {
        $response = HttpResponse::response()->statusCode(200)->primary(true);

        $this->assertArrayHasKey('primary', $response->toArray());
        $this->assertTrue($response->toArray()['primary']);
    }

    public function testPrimaryFalseIsSerialisedNotOmitted(): void
    {
        // false is meaningful: it marks a secondary action on a multi-action
        // expectation. Treating it like "unset" is how the selector goes missing.
        $response = HttpResponse::response()->statusCode(200)->primary(false);

        $arr = $response->toArray();
        $this->assertArrayHasKey('primary', $arr);
        $this->assertFalse($arr['primary']);
    }

    public function testPrimaryOmittedWhenNotSet(): void
    {
        $this->assertArrayNotHasKey('primary', HttpResponse::response()->statusCode(200)->toArray());
    }

    public function testTrailersAreSerialised(): void
    {
        $arr = HttpResponse::response()->statusCode(200)->trailer('X-Checksum', 'abc123')->toArray();

        $this->assertSame(['X-Checksum' => ['abc123']], $arr['trailers']);
    }

    public function testRepeatedTrailerAppendsLikeHeader(): void
    {
        $arr = HttpResponse::response()->trailer('X-T', 'a')->trailer('X-T', 'b')->toArray();

        $this->assertSame(['X-T' => ['a', 'b']], $arr['trailers']);
    }

    public function testEmptyStringTrailerValueSurvives(): void
    {
        // The emit guard is on the trailers map, not the value: an explicitly
        // empty trailer value must still reach the wire.
        $arr = HttpResponse::response()->trailer('X-Empty', '')->toArray();

        $this->assertSame(['X-Empty' => ['']], $arr['trailers']);
    }

    public function testTrailersOmittedWhenNotSet(): void
    {
        $this->assertArrayNotHasKey('trailers', HttpResponse::response()->statusCode(200)->toArray());
    }

    public function testGenerateFromSchemaIsSerialised(): void
    {
        $schema = '{"type":"object","properties":{"id":{"type":"integer"}}}';
        $arr = HttpResponse::response()->generateFromSchema($schema)->toArray();

        $this->assertSame($schema, $arr['generateFromSchema']);
    }

    public function testStatusCodeRangeIsSerialised(): void
    {
        $arr = HttpResponse::response()->statusCodeRange('200-299')->toArray();

        $this->assertSame('200-299', $arr['statusCodeRange']);
    }

    public function testRecoverAfterIsSerialised(): void
    {
        $arr = HttpResponse::response()
            ->statusCode(200)
            ->recoverAfter(
                RecoverAfter::failTimes(2)
                    ->failResponse(HttpResponse::response()->statusCode(503))
                    ->idempotencyHeader('X-Idempotency-Key'),
            )
            ->toArray();

        $this->assertSame(
            [
                'failTimes' => 2,
                'failResponse' => ['statusCode' => 503],
                'idempotencyHeader' => 'X-Idempotency-Key',
            ],
            $arr['recoverAfter'],
        );
    }

    public function testRecoverAfterZeroFailTimesIsSerialisedNotOmitted(): void
    {
        // failTimes <= 0 makes recoverAfter inert. That is a configuration, not
        // an absence — dropping it via a truthy guard would change its meaning.
        $arr = RecoverAfter::failTimes(0)->toArray();

        $this->assertArrayHasKey('failTimes', $arr);
        $this->assertSame(0, $arr['failTimes']);
    }

    public function testMatchesTheSharedGeneratedFixture(): void
    {
        // The builder inputs are LITERAL and the expected side is READ from the shared corpus.
        // Deriving the inputs from the fixture too would make this a self-comparison: both sides
        // would move together and a fixture change could never redden it.
        $built = HttpResponse::response()
            ->statusCodeRange('200-299')
            ->generateFromSchema('{"type":"object","properties":{"id":{"type":"integer"}}}')
            ->recoverAfter(
                RecoverAfter::failTimes(2)
                    ->failResponse(HttpResponse::response()->statusCode(503))
                    ->idempotencyHeader('X-Idempotency-Key'),
            )
            ->primary(false)
            ->toArray();

        // Sorted-then-strict: key order differs (the fixture leads with statusCodeRange, the
        // builder emits primary first) but types must still match exactly — primary is false and
        // failTimes is 0-capable, and a loose comparison would stop distinguishing those.
        $this->assertSame(
            SharedFixtures::sortedDeep(
                SharedFixtures::action('action_response_generated.json', 'httpResponse', 4),
            ),
            SharedFixtures::sortedDeep($built),
        );
    }

    public function testNewFieldsOmittedWhenNotSet(): void
    {
        $arr = HttpResponse::response()->statusCode(200)->toArray();

        $this->assertArrayNotHasKey('generateFromSchema', $arr);
        $this->assertArrayNotHasKey('statusCodeRange', $arr);
        $this->assertArrayNotHasKey('recoverAfter', $arr);
    }
}
