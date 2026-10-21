<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use MockServer\DateTimeMatcher;
use MockServer\Expectation;
use MockServer\HttpRequest;
use MockServer\Tests\Support\JsonCanon;
use MockServer\Tests\Support\SharedFixtures;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

class DateTimeMatcherTest extends TestCase
{
    private const FIXTURE = 'matcher_request_date_time.json';

    public function testConstructorsWriteOnlyTheOperator(): void
    {
        $this->assertSame(['before' => 'now'], DateTimeMatcher::before('now')->toArray());
        $this->assertSame(['after' => 'now -1 hours'], DateTimeMatcher::after('now -1 hours')->toArray());
        $this->assertSame(
            ['equalToDateTime' => '2026-10-21T00:00:00Z'],
            DateTimeMatcher::equalToDateTime('2026-10-21T00:00:00Z')->toArray(),
        );
    }

    public function testEveryOptionIsWrittenWhenSet(): void
    {
        $matcher = DateTimeMatcher::equalToDateTime('now +1 days')
            ->withActualFormat('rfc1123')
            ->withTruncateExpected('first day of month')
            ->withTruncateActual('startOfDay')
            ->withApplyTruncationLast(true)
            ->withZone('Europe/London')
            ->withNot(true);

        $this->assertSame([
            'not' => true,
            'equalToDateTime' => 'now +1 days',
            'actualFormat' => 'rfc1123',
            'truncateExpected' => 'first day of month',
            'truncateActual' => 'startOfDay',
            'applyTruncationLast' => true,
            'zone' => 'Europe/London',
        ], $matcher->toArray());
        $this->assertSame(
            '{"not":true,"equalToDateTime":"now +1 days","actualFormat":"rfc1123",'
            . '"truncateExpected":"first day of month","truncateActual":"startOfDay",'
            . '"applyTruncationLast":true,"zone":"Europe\/London"}',
            json_encode($matcher, JSON_THROW_ON_ERROR),
        );
        $this->assertSame('equalToDateTime', $matcher->getOperator());
        $this->assertSame('now +1 days', $matcher->getExpected());
        $this->assertSame('rfc1123', $matcher->getActualFormat());
        $this->assertSame('first day of month', $matcher->getTruncateExpected());
        $this->assertSame('startOfDay', $matcher->getTruncateActual());
        $this->assertTrue($matcher->getApplyTruncationLast());
        $this->assertSame('Europe/London', $matcher->getZone());
        $this->assertTrue($matcher->getNot());
    }

    public function testNotIsWrittenOnlyWhenTrueButApplyTruncationLastFalseIsWritten(): void
    {
        $this->assertSame(['after' => 'now'], DateTimeMatcher::after('now')->withNot(false)->toArray());
        $this->assertSame(
            ['after' => 'now', 'applyTruncationLast' => false],
            DateTimeMatcher::after('now')->withApplyTruncationLast(false)->toArray(),
        );
    }

    public function testWithMethodsReturnNewInstancesAndLeaveTheOriginalUnchanged(): void
    {
        $original = DateTimeMatcher::before('now');
        $copies = [
            $original->withActualFormat('rfc1123'),
            $original->withTruncateExpected('startOfDay'),
            $original->withTruncateActual('startOfDay'),
            $original->withApplyTruncationLast(true),
            $original->withZone('UTC'),
            $original->withNot(true),
        ];

        foreach ($copies as $copy) {
            $this->assertNotSame($original, $copy);
            $this->assertNotSame($original->toArray(), $copy->toArray());
        }
        $this->assertSame(['before' => 'now'], $original->toArray());
    }

    public function testBuilderOutputRoundTripsThroughJsonAndTheModel(): void
    {
        $matcher = DateTimeMatcher::before('2026-10-21T00:00:00Z')
            ->withActualFormat('rfc1123')
            ->withZone('UTC')
            ->withNot(true);

        $decoded = json_decode(json_encode($matcher, JSON_THROW_ON_ERROR), true, 512, JSON_THROW_ON_ERROR);
        $read = DateTimeMatcher::fromArray($decoded);

        $this->assertNotNull($read);
        $this->assertSame($matcher->toArray(), $read->toArray());
    }

    /**
     * @return iterable<string, array{mixed}>
     */
    public static function notADateTimeMatcherProvider(): iterable
    {
        yield 'plain string with operator prefix' => ['before:2026-01-01'];
        yield 'empty object' => [[]];
        yield 'list' => [['now']];
        yield 'no operator' => [['actualFormat' => 'rfc1123']];
        yield 'two operators' => [['before' => 'now', 'after' => 'now -1 days']];
        yield 'operator not a string' => [['after' => 5]];
        yield 'combined with value' => [['after' => 'now', 'value' => 'x']];
        yield 'combined with schema' => [['after' => 'now', 'schema' => ['type' => 'string']]];
        yield 'combined with optional' => [['after' => 'now', 'optional' => true]];
        yield 'nottable string' => [['not' => true, 'value' => 'x']];
        yield 'mistyped option' => [['after' => 'now', 'applyTruncationLast' => 'true']];
        yield 'not false is never written' => [['after' => 'now', 'not' => false]];
        yield 'unknown field' => [['after' => 'now', 'parameterStyle' => 'form']];
    }

    #[DataProvider('notADateTimeMatcherProvider')]
    public function testFromArrayReturnsNullForAnythingItCannotWriteBackUnchanged(mixed $data): void
    {
        $this->assertNull(DateTimeMatcher::fromArray($data));
    }

    public function testBuildersAcceptTheMatcherOnEveryValueCollection(): void
    {
        $request = HttpRequest::request()
            ->path('/orders/{since}')
            ->pathParameter('since', DateTimeMatcher::after('2026-01-01'))
            ->queryStringParameterMatcher('from', DateTimeMatcher::after('now -1 days'))
            ->queryStringParameter('from', 'today')
            ->headerMatcher('X-Issued-At', DateTimeMatcher::after('now -1 hours'))
            ->cookieMatcher('seen', DateTimeMatcher::before('now')->withNot(true));

        $this->assertSame(
            '{"path":"\/orders\/{since}",'
            . '"pathParameters":{"since":[{"after":"2026-01-01"}]},'
            . '"queryStringParameters":{"from":[{"after":"now -1 days"},"today"]},'
            . '"headers":{"X-Issued-At":[{"after":"now -1 hours"}]},'
            . '"cookies":{"seen":[{"not":true,"before":"now"}]}}',
            json_encode($request, JSON_THROW_ON_ERROR),
        );
        $this->assertInstanceOf(DateTimeMatcher::class, $request->getPathParameters()['since'][0]);
    }

    public function testExistingBuilderSignaturesAreUnchanged(): void
    {
        $string = static fn (string $class, string $method, int $param): string
            => (string) (new \ReflectionMethod($class, $method))->getParameters()[$param]->getType();

        $this->assertSame('string', $string(HttpRequest::class, 'header', 1));
        $this->assertSame('string', $string(HttpRequest::class, 'queryStringParameter', 1));
        $this->assertSame('string', $string(HttpRequest::class, 'cookie', 1));
        $this->assertSame(DateTimeMatcher::class, $string(HttpRequest::class, 'headerMatcher', 1));
        $this->assertSame(DateTimeMatcher::class, $string(HttpRequest::class, 'queryStringParameterMatcher', 1));
        $this->assertSame(DateTimeMatcher::class, $string(HttpRequest::class, 'cookieMatcher', 1));
    }

    public function testPlainStringWithOperatorPrefixStaysAPlainString(): void
    {
        $request = HttpRequest::request()->header('X-Plain', 'before:2026-01-01');
        $this->assertSame(['headers' => ['X-Plain' => ['before:2026-01-01']]], $request->toArray());

        $read = HttpRequest::fromArray(['headers' => ['X-Plain' => ['before:2026-01-01']]]);
        $this->assertSame(['before:2026-01-01'], $read->getHeaders()['X-Plain']);
    }

    public function testSharedFixtureReadsIntoTheTypedModelAndWritesBackUnchanged(): void
    {
        $wire = SharedFixtures::action(self::FIXTURE, 'httpRequest', 5);
        $request = HttpRequest::fromArray($wire);

        $headers = $request->getHeaders();
        $this->assertInstanceOf(DateTimeMatcher::class, $headers['X-Issued-At'][0]);
        $this->assertSame('after', $headers['X-Issued-At'][0]->getOperator());
        $this->assertInstanceOf(DateTimeMatcher::class, $headers['If-Modified-Since'][0]);
        $this->assertSame('rfc1123', $headers['If-Modified-Since'][0]->getActualFormat());
        $this->assertSame(['before:2026-01-01'], $headers['X-Plain']);

        $from = $request->getQueryStringParameters()['from'][0];
        $this->assertInstanceOf(DateTimeMatcher::class, $from);
        $this->assertSame('Europe/London', $from->getZone());

        $seen = $request->getCookies()['seen'];
        $this->assertInstanceOf(DateTimeMatcher::class, $seen);
        $this->assertTrue($seen->getNot());
        $this->assertTrue($seen->getApplyTruncationLast());

        $this->assertSame(
            JsonCanon::canon($wire),
            JsonCanon::decode(json_encode($request, JSON_THROW_ON_ERROR)),
        );
    }

    public function testBuildersReproduceTheSharedFixture(): void
    {
        $wire = SharedFixtures::action(self::FIXTURE, 'httpRequest', 5);

        $built = HttpRequest::request()
            ->method('GET')
            ->path('/date-time')
            ->headerMatcher('X-Issued-At', DateTimeMatcher::after('now -1 hours'))
            ->headerMatcher('If-Modified-Since', DateTimeMatcher::before('2026-10-21T00:00:00Z')->withActualFormat('rfc1123'))
            ->header('X-Plain', 'before:2026-01-01')
            ->queryStringParameterMatcher(
                'from',
                DateTimeMatcher::after('2026-01-01')->withActualFormat('dd/MM/yyyy')->withZone('Europe/London'),
            )
            ->cookieMatcher(
                'seen',
                DateTimeMatcher::equalToDateTime('now +1 days')
                    ->withNot(true)
                    ->withTruncateExpected('first day of month')
                    ->withTruncateActual('startOfDay')
                    ->withApplyTruncationLast(true),
            )
            ->toArray();

        // cookieMatcher() writes the list form {name: [value]}; the fixture uses the single-value form.
        $this->assertSame(JsonCanon::canon($wire['cookies']['seen']), JsonCanon::canon($built['cookies']['seen'][0]));
        unset($wire['cookies'], $built['cookies']);
        $this->assertSame(JsonCanon::canon($wire), JsonCanon::canon($built));
    }

    public function testListFormEntriesAndOtherObjectFormsAreKept(): void
    {
        $wire = [
            'headers' => [
                ['name' => 'X-Issued-At', 'values' => [['after' => 'now -1 hours'], ['not' => true, 'value' => 'x']]],
            ],
            'queryStringParameters' => [
                'id' => [['schema' => ['type' => 'integer']], ['after' => 'now', 'value' => 'rejected-by-server']],
            ],
        ];
        $request = HttpRequest::fromArray($wire);

        $values = $request->getHeaders()[0]['values'];
        $this->assertInstanceOf(DateTimeMatcher::class, $values[0]);
        $this->assertSame(['not' => true, 'value' => 'x'], $values[1]);
        $this->assertSame($wire['queryStringParameters'], $request->getQueryStringParameters());
        $this->assertSame(JsonCanon::canon($wire), JsonCanon::canon($request->toArray()));
    }

    public function testExpectationRawReplayAndTypedRequestBothCarryTheFixture(): void
    {
        $fixture = SharedFixtures::expectation(self::FIXTURE);
        $expectation = Expectation::fromArray($fixture);

        $this->assertSame(
            JsonCanon::canon($fixture),
            JsonCanon::decode(json_encode($expectation, JSON_THROW_ON_ERROR)),
        );
        $typed = $expectation->getHttpRequest();
        $this->assertNotNull($typed);
        $this->assertInstanceOf(DateTimeMatcher::class, $typed->getHeaders()['X-Issued-At'][0]);
        $this->assertSame(JsonCanon::canon($fixture['httpRequest']), JsonCanon::canon($typed->toArray()));
    }
}
