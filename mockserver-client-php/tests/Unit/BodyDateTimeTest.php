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

/**
 * A JSON_PATH or XPATH body can compare the value it selects as a date-time,
 * carried under dateTime in the same object form as a date-time header value.
 */
class BodyDateTimeTest extends TestCase
{
    public function testJsonPathBodyWritesTheDateTimeObjectForm(): void
    {
        $body = HttpRequest::jsonPathBody('$.order.created', DateTimeMatcher::after('now -1 days')->withZone('Europe/London'));

        $this->assertSame(
            ['type' => 'JSON_PATH', 'jsonPath' => '$.order.created', 'dateTime' => ['after' => 'now -1 days', 'zone' => 'Europe/London']],
            $body,
        );
    }

    public function testXPathBodyWritesTheDateTimeObjectForm(): void
    {
        $request = HttpRequest::request()
            ->path('/orders')
            ->matcherBody(HttpRequest::xpathBody('/order/@expires', DateTimeMatcher::before('2026-01-01')->withNot(true)));

        $this->assertSame(
            ['type' => 'XPATH', 'xpath' => '/order/@expires', 'dateTime' => ['not' => true, 'before' => '2026-01-01']],
            $request->toArray()['body'],
        );
    }

    public function testWithoutADateTimeNothingExtraIsWritten(): void
    {
        $this->assertSame(['type' => 'JSON_PATH', 'jsonPath' => '$.a'], HttpRequest::jsonPathBody('$.a'));
        $this->assertSame(['type' => 'XPATH', 'xpath' => '/a'], HttpRequest::xpathBody('/a'));
    }

    /**
     * @return array<string, array{string}>
     */
    public static function fixtures(): array
    {
        return [
            'json path' => ['matcher_body_json_path_date_time.json'],
            'xpath' => ['matcher_body_xpath_date_time.json'],
        ];
    }

    #[DataProvider('fixtures')]
    public function testSharedFixtureRoundTrips(string $fixtureName): void
    {
        $fixture = SharedFixtures::expectation($fixtureName);
        $expectation = Expectation::fromArray($fixture);

        $this->assertSame(
            JsonCanon::canon($fixture),
            JsonCanon::decode(json_encode($expectation, JSON_THROW_ON_ERROR)),
        );
        $typed = $expectation->getHttpRequest();
        $this->assertNotNull($typed);
        $this->assertSame(JsonCanon::canon($fixture['httpRequest']), JsonCanon::canon($typed->toArray()));
    }
}
