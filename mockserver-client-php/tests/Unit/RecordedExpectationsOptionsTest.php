<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Middleware;
use GuzzleHttp\Psr7\Response;
use MockServer\HttpRequest;
use MockServer\MockServerClient;
use MockServer\RecordedExpectationsOptions;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

/**
 * Retrieving recorded expectations with RecordedExpectationsOptions: the exact
 * query string sent, and the rejections that must send nothing.
 */
final class RecordedExpectationsOptionsTest extends TestCase
{
    private const RECORDED = 'type=RECORDED_EXPECTATIONS&format=JSON';

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

    public function testSendsEveryOptionWithARequestMatcherAndReturnsTheSequence(): void
    {
        $json = '[{"httpRequest":{"method":"GET","path":"/jobs/1"},'
            . '"httpResponses":[{"statusCode":200,"body":"pending"},{"statusCode":200,"body":"done"}],'
            . '"responseMode":"SWITCH","times":{"unlimited":true}}]';
        $history = [];
        $client = $this->client([new Response(200, [], $json)], $history);

        $recorded = $client->retrieveRecordedExpectationsWithOptions(
            HttpRequest::request()->path('/jobs/.*'),
            RecordedExpectationsOptions::recordedExpectationsOptions()
                ->withConsolidate()
                ->withParameterize()
                ->withRepeatsAsScenarios()
                ->withCaptureHeaders('X-Tenant', 'Accept')
                ->withExtractBodiesLargerThan(1024),
        );

        $request = $history[0]['request'];
        $this->assertSame('PUT', $request->getMethod());
        $this->assertSame(
            self::RECORDED . '&consolidate=true&parameterize=true&repeatsAsScenarios=true'
                . '&captureHeaders=X-Tenant%2CAccept&extractBodiesLargerThan=1024',
            $request->getUri()->getQuery(),
        );
        $this->assertSame(['path' => '/jobs/.*'], json_decode((string) $request->getBody(), true));
        $this->assertCount(1, $recorded);
        $this->assertSame('SWITCH', $recorded[0]['responseMode']);
        $this->assertCount(2, $recorded[0]['httpResponses']);
    }

    /**
     * @return array<string, array{RecordedExpectationsOptions, string}>
     */
    public static function optionQueries(): array
    {
        $options = static fn(): RecordedExpectationsOptions => RecordedExpectationsOptions::recordedExpectationsOptions();
        return [
            'no options' => [$options(), self::RECORDED],
            'false is not sent' => [$options()->withRepeatsAsScenarios(false)->withConsolidate(false), self::RECORDED],
            'empty capture headers keeps none' => [$options()->withCaptureHeaders(), self::RECORDED . '&captureHeaders='],
            'zero threshold' => [$options()->withExtractBodiesLargerThan(0), self::RECORDED . '&extractBodiesLargerThan=0'],
        ];
    }

    #[DataProvider('optionQueries')]
    public function testSendsTheQueryForEachOption(RecordedExpectationsOptions $options, string $expectedQuery): void
    {
        $history = [];
        $client = $this->client([new Response(200, [], '[]')], $history);

        $client->retrieveRecordedExpectationsWithOptions(null, $options);

        $this->assertCount(1, $history);
        $this->assertSame($expectedQuery, $history[0]['request']->getUri()->getQuery());
    }

    /**
     * @return array<string, array{callable(): mixed}>
     */
    public static function rejections(): array
    {
        $options = static fn(): RecordedExpectationsOptions => RecordedExpectationsOptions::recordedExpectationsOptions();
        return [
            'blank header name' => [static fn() => $options()->withCaptureHeaders('X-Tenant', ' ')],
            'header name with a comma' => [static fn() => $options()->withCaptureHeaders('X-Tenant,Accept')],
            'negative threshold' => [static fn() => $options()->withExtractBodiesLargerThan(-1)],
        ];
    }

    #[DataProvider('rejections')]
    public function testInvalidOptionIsRejectedWhenSet(callable $build): void
    {
        $this->expectException(\InvalidArgumentException::class);
        $build();
    }
}
