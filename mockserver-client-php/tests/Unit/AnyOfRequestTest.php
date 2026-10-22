<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use MockServer\Diagnostics\AnyOfBranchComparison;
use MockServer\Diagnostics\ExplainedExpectation;
use MockServer\Expectation;
use MockServer\HttpRequest;
use MockServer\Tests\Support\JsonCanon;
use PHPUnit\Framework\TestCase;

class AnyOfRequestTest extends TestCase
{
    public function testAnyOfSerialisesOnlyTheBranches(): void
    {
        $request = HttpRequest::anyOf(
            HttpRequest::request()->method('GET')->path('/pets'),
            HttpRequest::request()->method('GET')->path('/animals'),
        );

        $this->assertSame([
            'anyOf' => [
                ['method' => 'GET', 'path' => '/pets'],
                ['method' => 'GET', 'path' => '/animals'],
            ],
        ], $request->toArray());
        $this->assertTrue($request->isAnyOf());
        $this->assertFalse($request->isConditional());
        $this->assertFalse(HttpRequest::request()->path('/x')->isAnyOf());
        $this->assertArrayNotHasKey('anyOf', HttpRequest::request()->path('/x')->toArray());
    }

    public function testNegatedAnyOf(): void
    {
        $this->assertSame(
            ['not' => true, 'anyOf' => [['path' => '/health']]],
            HttpRequest::anyOf(HttpRequest::request()->path('/health'))->not()->toArray(),
        );
    }

    public function testFromArrayReadsEveryBranchKindAsTypedRequests(): void
    {
        $wire = [
            'not' => true,
            'anyOf' => [
                ['method' => 'GET', 'path' => '/pets', 'headers' => ['X-Tenant' => ['acme']]],
                ['specUrlOrPayload' => 'https://example.com/o.json', 'operationId' => 'listPets'],
                ['if' => ['method' => 'POST'], 'then' => ['path' => '/orders']],
                ['not' => true, 'anyOf' => [['path' => '/admin/.*'], ['path' => '/internal/.*']]],
            ],
        ];
        $request = HttpRequest::fromArray($wire);

        $branches = $request->getAnyOf();
        $this->assertNotNull($branches);
        $this->assertCount(4, $branches);
        $this->assertSame('/pets', $branches[0]->getPath());
        $this->assertTrue($branches[1]->isOpenAPI());
        $this->assertTrue($branches[2]->isConditional());
        $this->assertTrue($branches[3]->isAnyOf());
        $this->assertSame('/internal/.*', $branches[3]->getAnyOf()[1]->getPath());
        $this->assertSame(
            JsonCanon::canon($wire),
            JsonCanon::decode(json_encode($request, JSON_THROW_ON_ERROR)),
        );
    }

    public function testAConditionalBranchHoldingAnyOfRoundTrips(): void
    {
        $wire = ['if' => ['method' => 'GET'], 'then' => ['anyOf' => [['path' => '/a'], ['path' => '/b']]]];
        $request = HttpRequest::fromArray($wire);

        $this->assertTrue($request->getThenRequest()?->isAnyOf() ?? false);
        $this->assertSame(JsonCanon::canon($wire), JsonCanon::canon($request->toArray()));
    }

    public function testAnEmptyAnyOfIsSentForTheServerToRejectNotDroppedToMatchAll(): void
    {
        // {} would match every request; "anyOf": [] is rejected by the server
        $this->assertSame('{"anyOf":[]}', json_encode(HttpRequest::anyOf(), JSON_THROW_ON_ERROR));
        $this->assertSame('{"anyOf":[]}', json_encode(HttpRequest::fromArray(['anyOf' => []]), JSON_THROW_ON_ERROR));
        $expectation = (new Expectation())->httpRequest(HttpRequest::anyOf());
        $this->assertSame('{"anyOf":[]}', json_encode($expectation->toArray()['httpRequest'], JSON_THROW_ON_ERROR));
    }

    public function testAMalformedAnyOfIsKeptAsRead(): void
    {
        $wire = ['anyOf' => ['not-an-object']];
        $request = HttpRequest::fromArray($wire);

        $this->assertFalse($request->isAnyOf());
        $this->assertSame($wire, $request->toArray());
    }

    public function testExplainedExpectationReadsTheClosestBranch(): void
    {
        $explained = ExplainedExpectation::fromArray([
            'expectationId' => 'any-of',
            'anyOf' => ['closestBranch' => 1, 'branches' => [
                ['branch' => 0, 'matches' => false, 'differingFieldCount' => 2, 'differingFields' => ['method', 'path']],
                ['branch' => 1, 'matches' => false, 'differingFieldCount' => 1, 'differingFields' => ['headers']],
            ]],
        ]);

        $this->assertInstanceOf(AnyOfBranchComparison::class, $explained->anyOf);
        $this->assertSame(1, $explained->anyOf->closestBranch);
        $this->assertSame(['headers'], $explained->anyOf->branches[1]['differingFields']);
        $this->assertNull(ExplainedExpectation::fromArray(['expectationId' => 'plain'])->anyOf);
    }
}
