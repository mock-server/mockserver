<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use MockServer\HttpError;
use MockServer\HttpForward;
use MockServer\HttpRequest;
use MockServer\HttpResponse;
use MockServer\Tests\Support\FidelityComparator;
use PHPUnit\Framework\Attributes\DataProvider;
use PHPUnit\Framework\TestCase;

/**
 * TYPED-MODEL fidelity gate for the PHP client (manifest key "php-typed").
 *
 * WHY THIS EXISTS. The sibling {@see RoundTripFidelityTest} deserialises each
 * shared fixture with {@see \MockServer\Expectation::fromArray()}, which stores
 * the decoded array VERBATIM in {@code rawData} and replays it unchanged. That
 * harness therefore records ZERO gaps for every fixture BY CONSTRUCTION — it can
 * never detect a field that the TYPED builders ({@see HttpResponse},
 * {@see HttpForward}, {@see HttpError}, {@see HttpRequest}) fail to model,
 * because none of those classes is on the path it exercises. It is a tautology
 * with respect to typed-model coverage.
 *
 * WHAT THIS DOES INSTEAD. For every shared fixture in
 * {@code test-fixtures/expectations/*.json} this reconstructs each action /
 * matcher block THROUGH THE TYPED MODEL — not via raw replay — and diffs the
 * rebuilt structure back against the fixture (the SERVER-SCHEMA side of the
 * contract). A field the typed class does not DECLARE is silently dropped by the
 * typed round-trip and shows up as a concrete diff path. The cases are derived
 * from the fixture corpus (server schema), never from the client's own key list,
 * so a NEW server field added to the corpus that the typed model has not caught
 * up to fails here rather than passing silently.
 *
 * HOW THE TYPED ROUND-TRIP IS DRIVEN. {@see rebuildTyped()} instantiates the
 * target typed class and, using reflection, copies across ONLY the properties the
 * class actually declares (recursing into declared nested typed properties such
 * as {@code Delay} / {@code ConnectionOptions} / {@code RecoverAfter}). It then
 * serialises via the class's own {@code toArray()}. This makes the reconstruction
 * a faithful mirror of "what the typed model can carry": a declared field
 * survives, an undeclared one is dropped, and a value whose JSON shape is
 * incompatible with the declared property type (e.g. a NottableString object
 * where the model declares {@code ?string}) is dropped too. No field is
 * hand-mapped, so the harness cannot accidentally launder a gap.
 *
 * The zero-vs-nonzero difference from the raw harness is the whole point: this
 * one produces REAL diffs (see {@see GAPS}) that must be individually excused,
 * and the {@see testTypedDropIsFlagged()} positive control proves the gate fires
 * when a typed builder drops a field it is supposed to carry.
 */
final class TypedRoundTripFidelityTest extends TestCase
{
    /**
     * Action / matcher keys that map to a typed builder this harness gates, and
     * the class that models each. Keys absent from a fixture are skipped; keys
     * present but not listed here (gRPC, DNS, LLM, SSE, WebSocket, templates,
     * callbacks, ...) are out of scope for THIS gate and covered by their own
     * per-class unit tests.
     *
     * @var array<string, class-string>
     */
    private const TYPED_ACTIONS = [
        'httpRequest' => HttpRequest::class,
        'httpResponse' => HttpResponse::class,
        'httpForward' => HttpForward::class,
        'httpError' => HttpError::class,
    ];

    /**
     * Server-schema fields the typed model legitimately does not carry today.
     *
     * Each entry excuses a real diff produced by the typed round-trip above; the
     * {@see testNoStaleGapEntries()} ratchet fails the build if an entry stops
     * excusing any diff (i.e. the typed model was extended to cover it and the
     * entry should be deleted). These are all on the request MATCHER, whose PHP
     * typed model ({@see HttpRequest}) declares a deliberately narrow field set:
     *
     *   - dnsClass/dnsName/dnsType : DNS-query matcher fields (no typed setter).
     *   - protocol                 : request protocol matcher (unmodelled).
     *   - method/path              : dropped ONLY when a fixture encodes them as a
     *                                NottableString object {not,value}; the model
     *                                declares them as plain ?string, so the object
     *                                form cannot be represented and is dropped.
     *
     * The httpResponse / httpForward / httpError typed models cover their entire
     * fixture surface, so they contribute NO entries — deliberately, to show this
     * ledger tracks genuine model gaps rather than blanket-excusing an action.
     *
     * @var list<string>
     */
    private const GAPS = [
        'httpRequest.dnsClass',
        'httpRequest.dnsName',
        'httpRequest.dnsType',
        'httpRequest.protocol',
        'httpRequest.method',
        'httpRequest.path',
    ];

    private static function repoRoot(): string
    {
        // tests/Unit -> tests -> mockserver-client-php -> repo root
        return __DIR__ . '/../../../';
    }

    /**
     * @return list<string> absolute fixture paths, excluding the manifest.
     */
    private static function fixtureFiles(): array
    {
        $glob = self::repoRoot() . 'test-fixtures/expectations/*.json';
        $files = glob($glob);
        if ($files === false) {
            return [];
        }
        $files = array_filter(
            $files,
            static fn(string $f): bool => basename($f) !== 'known-gaps.json',
        );
        sort($files);
        return array_values($files);
    }

    // ------------------------------------------------------------------
    // Typed reconstruction via the declared-property surface of the model.
    // ------------------------------------------------------------------

    /**
     * Rebuild a typed object from a decoded JSON object by copying across ONLY
     * the properties the class declares, recursing into declared nested typed
     * properties, then return its {@code toArray()} projection.
     *
     * A declared scalar/array property is raw-set (headers, cookies, body, jwt,
     * socketAddress keep their JSON shape); a declared property whose type is
     * another MockServer typed class is reconstructed recursively; anything the
     * class does not declare, or a value whose type the declared property
     * rejects, is left unset and therefore absent from {@code toArray()}.
     *
     * @param class-string $class
     * @param array<string, mixed> $data
     * @return array<string, mixed>
     */
    private static function typedRoundTrip(string $class, array $data): array
    {
        $obj = self::rebuildTyped($class, $data);
        /** @var array<string, mixed> $out */
        $out = $obj->toArray();
        return $out;
    }

    /**
     * @param class-string $class
     * @param array<string, mixed> $data
     */
    private static function rebuildTyped(string $class, array $data): object
    {
        $ref = new \ReflectionClass($class);
        // Bypass constructors (e.g. Delay requires args) — this test cares only
        // about which declared fields the typed model can hold, not construction.
        $obj = $ref->newInstanceWithoutConstructor();

        foreach ($ref->getProperties() as $prop) {
            $name = $prop->getName();
            if (!array_key_exists($name, $data)) {
                continue;
            }
            $value = $data[$name];
            // No setAccessible() needed: reflection has written private
            // properties directly since PHP 8.1 (the client's floor), and the
            // call is a deprecated no-op from PHP 8.5.

            $nestedClass = self::nestedTypedClass($prop);
            if ($nestedClass !== null && FidelityComparator::isJsonObject($value)) {
                /** @var array<string, mixed> $value */
                $prop->setValue($obj, self::rebuildTyped($nestedClass, $value));
                continue;
            }

            // Raw-set a scalar/array/union property. A typed property (e.g.
            // ?string $method) rejects an incompatible JSON shape (a
            // NottableString object) with a TypeError — that is a genuine
            // typed-model gap, so swallow it and leave the field unset.
            try {
                $prop->setValue($obj, $value);
            } catch (\TypeError) {
                // field cannot be represented by the typed model -> dropped
            }
        }

        return $obj;
    }

    /**
     * The MockServer typed class a property recurses into, or null if the
     * property is a scalar/array/union/builtin that should be raw-set.
     */
    private static function nestedTypedClass(\ReflectionProperty $prop): ?string
    {
        $type = $prop->getType();
        if (!$type instanceof \ReflectionNamedType || $type->isBuiltin()) {
            return null;
        }
        $name = $type->getName();
        if (!str_starts_with($name, 'MockServer\\') || !class_exists($name)) {
            return null;
        }
        return $name;
    }

    /**
     * Compute the typed-round-trip diff paths for one fixture, each prefixed by
     * its action key (e.g. `httpRequest.pathParameters`) and with any `[ADDED]`
     * suffix preserved.
     *
     * @return list<string>
     */
    private static function computeTypedDiffs(string $fixturePath): array
    {
        $json = file_get_contents($fixturePath);
        if ($json === false) {
            throw new \RuntimeException("Cannot read fixture: $fixturePath");
        }
        /** @var array<string, mixed> $input */
        $input = json_decode($json, true, 512, JSON_THROW_ON_ERROR);

        $res = [];
        foreach (self::TYPED_ACTIONS as $key => $class) {
            if (!array_key_exists($key, $input) || !FidelityComparator::isJsonObject($input[$key])) {
                continue;
            }
            /** @var array<string, mixed> $block */
            $block = $input[$key];
            $rebuilt = self::typedRoundTrip($class, $block);
            $res = array_merge($res, self::diffBlock($key, $block, $rebuilt));
        }
        return $res;
    }

    /**
     * Diff one action block field-by-field at the top level, delegating nested
     * values to the shared comparator with key-aware normalisation.
     *
     * Enumerating the block's own top-level keys (rather than handing the whole
     * block to {@see FidelityComparator::diffs()}) is deliberate: when the typed
     * model drops EVERY field of a block, its {@code toArray()} is the empty
     * array `[]`, which PHP/JSON cannot distinguish from an empty list. A
     * whole-object diff would then collapse a fully-emptied block to the single
     * parent path (e.g. `httpRequest`) instead of naming each dropped field
     * (`httpRequest.dnsName`, ...). Per-field enumeration keeps the gap ledger
     * precise and the ratchet honest. Nested full-drops cannot occur here — the
     * modelled nested typed objects (Delay/ConnectionOptions/RecoverAfter) and
     * the raw-set arrays (body/headers/socketAddress/jwt) never emptied by the
     * model — so the shared comparator handles everything below the top level.
     *
     * @param array<string, mixed> $in
     * @param array<string, mixed> $out
     * @return list<string>
     */
    private static function diffBlock(string $key, array $in, array $out): array
    {
        $res = [];
        foreach ($in as $field => $val) {
            if ($val === null) { // NORM: null == absent
                continue;
            }
            $p = "$key.$field";
            if (!array_key_exists($field, $out) || $out[$field] === null) {
                $res[] = $p; // server-schema field dropped by the typed model
                continue;
            }
            $res = array_merge($res, FidelityComparator::diffs(
                FidelityComparator::norm($val, (string) $field),
                FidelityComparator::norm($out[$field], (string) $field),
                $p,
            ));
        }
        foreach ($out as $field => $val) {
            if ($val === null) {
                continue;
            }
            if (!array_key_exists($field, $in) || $in[$field] === null) {
                $res[] = "$key.$field [ADDED]";
            }
        }
        return $res;
    }

    // ------------------------------------------------------------------
    // Data provider — one data set per fixture that carries a gated action.
    // ------------------------------------------------------------------

    /**
     * @return iterable<string, array{string}>
     */
    public static function fixtureProvider(): iterable
    {
        foreach (self::fixtureFiles() as $path) {
            $json = file_get_contents($path);
            if ($json === false) {
                continue;
            }
            $decoded = json_decode($json, true);
            if (!is_array($decoded)) {
                continue;
            }
            foreach (array_keys(self::TYPED_ACTIONS) as $key) {
                if (array_key_exists($key, $decoded) && FidelityComparator::isJsonObject($decoded[$key])) {
                    yield basename($path) => [$path];
                    break;
                }
            }
        }
    }

    /**
     * Every fixture must survive the TYPED round-trip with no UNEXCUSED diffs:
     * i.e. every server-schema field either survives reconstruction through the
     * typed model or is explicitly excused in {@see GAPS}.
     */
    #[DataProvider('fixtureProvider')]
    public function testTypedRoundTripFidelity(string $fixturePath): void
    {
        $unexcused = [];
        foreach (self::computeTypedDiffs($fixturePath) as $d) {
            $bare = str_replace(' [ADDED]', '', $d);
            if (!FidelityComparator::excused($bare, self::GAPS)) {
                $unexcused[] = $d;
            }
        }

        $this->assertSame(
            [],
            $unexcused,
            'Unexcused TYPED round-trip fidelity diffs for ' . basename($fixturePath)
            . ' (a server-schema field was dropped by the typed model): '
            . implode(', ', $unexcused),
        );
    }

    /**
     * POSITIVE CONTROL — proves this gate is NOT a tautology.
     *
     * Reconstructs a real response fixture through the typed model, then
     * simulates a typed-model regression by deleting one field the model is
     * supposed to carry ({@code statusCode}) from the rebuilt structure, and
     * asserts the comparator FLAGS the missing field. The raw-replay harness
     * cannot detect such a drop; this one does. (Deleting the field from the
     * projection is the observable equivalent of removing it from
     * {@see HttpResponse::toArray()} — do the latter by hand and this whole
     * class goes red for every fixture carrying that field, then green again on
     * restore.)
     */
    public function testTypedDropIsFlagged(): void
    {
        $path = self::repoRoot() . 'test-fixtures/expectations/response_static_full.json';
        $json = file_get_contents($path);
        self::assertNotFalse($json, "positive-control fixture missing: $path");
        /** @var array<string, mixed> $input */
        $input = json_decode($json, true, 512, JSON_THROW_ON_ERROR);
        self::assertArrayHasKey('httpResponse', $input);
        /** @var array<string, mixed> $block */
        $block = $input['httpResponse'];
        self::assertArrayHasKey('statusCode', $block, 'fixture must carry statusCode for this control');

        // Faithful typed round-trip: no unexcused diff (statusCode is modelled).
        $rebuilt = self::typedRoundTrip(HttpResponse::class, $block);
        $clean = FidelityComparator::diffs(
            FidelityComparator::norm($block),
            FidelityComparator::norm($rebuilt),
            'httpResponse',
        );
        self::assertNotContains(
            'httpResponse.statusCode',
            $clean,
            'statusCode is modelled by HttpResponse and must survive the typed round-trip',
        );

        // Simulate the typed model dropping statusCode -> the gate MUST flag it.
        $regressed = $rebuilt;
        unset($regressed['statusCode']);
        $flagged = FidelityComparator::diffs(
            FidelityComparator::norm($block),
            FidelityComparator::norm($regressed),
            'httpResponse',
        );
        self::assertContains(
            'httpResponse.statusCode',
            $flagged,
            'the typed fidelity gate failed to flag a dropped statusCode',
        );
    }

    /**
     * Ratchet: every {@see GAPS} entry must excuse at least one real diff across
     * the corpus. A stale entry means the typed model was extended to cover the
     * field and the entry should be removed — CI fails until it is.
     */
    public function testNoStaleGapEntries(): void
    {
        $allDiffs = [];
        foreach (self::fixtureFiles() as $path) {
            foreach (self::computeTypedDiffs($path) as $d) {
                $allDiffs[] = str_replace(' [ADDED]', '', $d);
            }
        }

        $stale = [];
        foreach (self::GAPS as $entry) {
            $used = false;
            foreach ($allDiffs as $diff) {
                if (FidelityComparator::excused($diff, [$entry])) {
                    $used = true;
                    break;
                }
            }
            if (!$used) {
                $stale[] = $entry;
            }
        }

        $this->assertSame(
            [],
            $stale,
            'Stale php-typed gap entries excusing no diff (remove them): ' . implode(', ', $stale),
        );
    }

    /**
     * Guards against a broken glob / path silently testing nothing: the typed
     * harness must exercise a non-trivial number of fixtures. A LOWER BOUND (not
     * an exact count) so growing the corpus never breaks this unrelated check.
     */
    public function testGatedFixtureCount(): void
    {
        $gated = iterator_to_array(self::fixtureProvider(), false);
        $this->assertGreaterThanOrEqual(
            40,
            count($gated),
            'Expected at least 40 fixtures carrying a typed-gated action/matcher block',
        );
    }
}
