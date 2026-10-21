import { describe, it, expect } from 'vitest';
import {
  buildExpectationJson,
  mergeUnmodeledFields,
  unmodeledFieldNames,
  jwtFaithfullyModeled,
  type StandardMatcher,
  type StandardActionPayload,
} from '../lib/standardCodegen';

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function baseMatcher(overrides?: Partial<StandardMatcher>): StandardMatcher {
  return {
    id: '',
    method: 'GET',
    path: '/api/test',
    headers: '',
    queryString: '',
    cookies: '',
    pathParams: '',
    body: '',
    bodyBinary: false,
    bodyMatcherType: 'string',
    priority: 0,
    times: 0,
    ...overrides,
  };
}

const staticAction = (
  extra?: Partial<StandardActionPayload>,
  status = 200,
): StandardActionPayload => ({
  type: 'static',
  static: { statusCode: status, body: '', contentType: '' },
  ...extra,
});

// ---------------------------------------------------------------------------
// mergeUnmodeledFields — the documented passthrough helper
// ---------------------------------------------------------------------------

describe('mergeUnmodeledFields — passthrough of fields the form does not model', () => {
  it('preserves scenario bindings while applying a status-code edit', () => {
    // A stateful mock registered by a client: only matches in the "PAID" state
    // of the "checkout" scenario, then advances it to "SHIPPED".
    const original = {
      httpRequest: { method: 'POST', path: '/checkout' },
      httpResponse: { statusCode: 200, body: 'ok' },
      scenarioName: 'checkout',
      scenarioState: 'PAID',
      newScenarioState: 'SHIPPED',
      namespace: 'orders',
      id: 'checkout-paid',
    };
    // Form re-emits the (modeled) response with a new status code.
    const formJson = buildExpectationJson(
      baseMatcher({ id: 'checkout-paid', method: 'POST', path: '/checkout' }),
      staticAction({ static: { statusCode: 201, body: 'ok', contentType: '' } }),
    );
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: true });

    expect((merged['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(201);
    // Scenario binding + namespace survive.
    expect(merged['scenarioName']).toBe('checkout');
    expect(merged['scenarioState']).toBe('PAID');
    expect(merged['newScenarioState']).toBe('SHIPPED');
    expect(merged['namespace']).toBe('orders');
    expect(merged['id']).toBe('checkout-paid');
  });

  it('preserves a response sequence (httpResponses/responseMode/responseWeights/switchAfter) when the action is unmodeled and only a matcher field is edited', () => {
    const original = {
      httpRequest: { method: 'GET', path: '/poll' },
      httpResponses: [
        { statusCode: 202, body: 'pending' },
        { statusCode: 200, body: 'done' },
      ],
      responseMode: 'WEIGHTED',
      responseWeights: [3, 1],
      switchAfter: 5,
      id: 'poll-seq',
    };
    // The form cannot model httpResponses, so it emits its default static
    // response — but actionModeled=false means the original action is kept.
    const formJson = buildExpectationJson(
      baseMatcher({ id: 'poll-seq', method: 'GET', path: '/poll/v2' }),
      staticAction(),
    );
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: false });

    // Matcher edit applied.
    expect((merged['httpRequest'] as Record<string, unknown>)['path']).toBe('/poll/v2');
    // Whole response sequence preserved, and no spurious singular httpResponse.
    expect(merged['httpResponses']).toEqual(original.httpResponses);
    expect(merged['responseMode']).toBe('WEIGHTED');
    expect(merged['responseWeights']).toEqual([3, 1]);
    expect(merged['switchAfter']).toBe(5);
    expect(merged['httpResponse']).toBeUndefined();
  });

  it('preserves crossProtocolScenarios', () => {
    const original = {
      httpRequest: { path: '/api/users' },
      httpResponse: { statusCode: 200 },
      crossProtocolScenarios: [
        { trigger: 'DNS_QUERY', matchPattern: 'api.example.com', scenarioName: 'DnsFlow', targetState: 'DnsObserved' },
      ],
      id: 'cross',
    };
    const formJson = buildExpectationJson(
      baseMatcher({ id: 'cross', path: '/api/users' }),
      staticAction(undefined, 204),
    );
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: true });

    expect((merged['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(204);
    expect(merged['crossProtocolScenarios']).toEqual(original.crossProtocolScenarios);
  });

  it('preserves unmodeled request-matcher fields (keepAlive / socketAddress / protocol) when a modeled matcher field is edited', () => {
    const original = {
      httpRequest: {
        method: 'GET',
        path: '/svc',
        keepAlive: true,
        socketAddress: { host: 'backend', port: 8443, scheme: 'HTTPS' },
        protocol: 'HTTP_2',
      },
      httpResponse: { statusCode: 200 },
      id: 'req-extras',
    };
    // Edit the path only.
    const formJson = buildExpectationJson(
      baseMatcher({ id: 'req-extras', method: 'GET', path: '/svc/v2' }),
      staticAction(),
    );
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: true });
    const req = merged['httpRequest'] as Record<string, unknown>;

    expect(req['path']).toBe('/svc/v2');
    expect(req['keepAlive']).toBe(true);
    expect(req['socketAddress']).toEqual({ host: 'backend', port: 8443, scheme: 'HTTPS' });
    expect(req['protocol']).toBe('HTTP_2');
  });

  it('preserves the grpcBidiResponse action when unmodeled', () => {
    const original = {
      httpRequest: { path: '/grpc' },
      grpcBidiResponse: { messages: [{ json: '{"a":1}' }] },
      id: 'bidi',
    };
    const formJson = buildExpectationJson(baseMatcher({ id: 'bidi', path: '/grpc/v2' }), staticAction());
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: false });

    expect(merged['grpcBidiResponse']).toEqual(original.grpcBidiResponse);
    expect(merged['httpResponse']).toBeUndefined();
    expect((merged['httpRequest'] as Record<string, unknown>)['path']).toBe('/grpc/v2');
  });

  it('replaces the action family when the form owns the action (switching static → forward drops the old response)', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200, body: 'old' },
      scenarioName: 'flow',
      id: 'switch',
    };
    const formJson = buildExpectationJson(
      baseMatcher({ id: 'switch', path: '/api' }),
      { type: 'forward', forward: { scheme: 'HTTPS', host: 'upstream', port: 443 } },
    );
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: true });

    expect(merged['httpResponse']).toBeUndefined();
    expect(merged['httpForward']).toEqual({ scheme: 'HTTPS', host: 'upstream', port: 443 });
    // Non-action passthrough still survives an action switch.
    expect(merged['scenarioName']).toBe('flow');
  });

  it('clears every mutually-exclusive action-slot field when the form owns the slot (incl. httpLlmResponse / *ObjectCallback / httpForwardValidateAction)', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpLlmResponse: { provider: 'openai', model: 'gpt-4o' },
      httpForwardObjectCallback: { clientId: 'c1' },
      httpForwardValidateAction: { host: 'up', port: 443 },
      id: 'slot',
    };
    const formJson = buildExpectationJson(baseMatcher({ id: 'slot', path: '/api' }), staticAction());
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: true });
    // The form's static response replaces the whole slot — no stale action lingers.
    expect(merged['httpLlmResponse']).toBeUndefined();
    expect(merged['httpForwardObjectCallback']).toBeUndefined();
    expect(merged['httpForwardValidateAction']).toBeUndefined();
    expect((merged['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(200);
  });

  it('honours a removal the form can express (clearing priority deletes it)', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200 },
      priority: 10,
      id: 'prio',
    };
    // matcher.priority defaults to 0 → buildExpectationJson omits `priority`.
    const formJson = buildExpectationJson(baseMatcher({ id: 'prio', path: '/api' }), staticAction());
    const merged = mergeUnmodeledFields(original, formJson, { actionModeled: true });

    expect(merged['priority']).toBeUndefined();
  });

  it('does not mutate the original or the form JSON', () => {
    const original = { httpRequest: { path: '/api' }, httpResponse: { statusCode: 200 }, scenarioName: 's' };
    const formJson = buildExpectationJson(baseMatcher({ path: '/api' }), staticAction(undefined, 201));
    const originalSnapshot = structuredClone(original);
    const formSnapshot = structuredClone(formJson);
    mergeUnmodeledFields(original, formJson, { actionModeled: true });
    expect(original).toEqual(originalSnapshot);
    expect(formJson).toEqual(formSnapshot);
  });
});

// ---------------------------------------------------------------------------
// buildExpectationJson integration — editOriginal threads the merge through the
// SAME path used by both the preview and the wire payload.
// ---------------------------------------------------------------------------

describe('buildExpectationJson editOriginal overlay (shared preview + wire path)', () => {
  it('merges onto the retained original when editOriginal is supplied', () => {
    const original = {
      httpRequest: { path: '/checkout' },
      httpResponse: { statusCode: 200 },
      scenarioName: 'checkout',
      scenarioState: 'PAID',
      id: 'x',
    };
    const merged = buildExpectationJson(
      baseMatcher({ id: 'x', path: '/checkout' }),
      staticAction({ editOriginal: original, editActionModeled: true }, 201),
    );
    expect((merged['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(201);
    expect(merged['scenarioName']).toBe('checkout');
    expect(merged['scenarioState']).toBe('PAID');
  });

  it('duplicate flow: passthrough carries over but the id is removed', () => {
    // Duplicate strips id from the value before it reaches the composer.
    const originalWithoutId = {
      httpRequest: { path: '/checkout' },
      httpResponse: { statusCode: 200 },
      scenarioName: 'checkout',
      scenarioState: 'PAID',
    };
    const merged = buildExpectationJson(
      // matcher.id is blank on a duplicate.
      baseMatcher({ id: '', path: '/checkout' }),
      staticAction({ editOriginal: originalWithoutId, editActionModeled: true }),
    );
    expect(merged['id']).toBeUndefined();
    expect(merged['scenarioName']).toBe('checkout');
    expect(merged['scenarioState']).toBe('PAID');
  });

  it('Quick-mock "Update mock" wire payload preserves scenarioName (overlay threaded through the quick static path)', () => {
    // Reproduces the reachable bypass: dashboard Edit → toggle to Quick mock →
    // click "Update mock". The Quick path builds a plain static action; the fix
    // spreads editOriginal/editActionModeled into it so the same merge runs.
    const original = {
      httpRequest: { method: 'GET', path: '/checkout' },
      httpResponse: { statusCode: 200, body: 'ok' },
      scenarioName: 'checkout',
      scenarioState: 'PAID',
      namespace: 'orders',
      crossProtocolScenarios: [{ trigger: 'HTTP_REQUEST', scenarioName: 'checkout', targetState: 'PAID' }],
      id: 'checkout-paid',
    };
    // The original action was a plain httpResponse → the form owns the slot
    // (editActionModeled true), exactly what ComposerView records on load.
    const quickUpdateAction: StandardActionPayload = {
      type: 'static',
      static: { statusCode: 201, body: 'ok', contentType: '' },
      editOriginal: original,
      editActionModeled: true,
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'checkout-paid', method: 'GET', path: '/checkout' }),
      quickUpdateAction,
    );
    // The status edit is applied…
    expect((wire['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(201);
    // …and NONE of the unmodeled fields are stripped.
    expect(wire['scenarioName']).toBe('checkout');
    expect(wire['scenarioState']).toBe('PAID');
    expect(wire['namespace']).toBe('orders');
    expect(wire['crossProtocolScenarios']).toEqual(original.crossProtocolScenarios);
    expect(wire['id']).toBe('checkout-paid');
  });

  it('Quick-mock update of a RECOGNISED non-static original (httpForward) preserves it — Quick may not own the slot for a non-httpResponse action', () => {
    // The reachable bug: httpForward is a recognised action (Advanced sets
    // editActionModeled=true), but Quick only authors a static response. Quick
    // must compute quickActionModeled=false here so the forward is preserved and
    // the futile Quick static default is dropped (Advanced is the conversion path).
    const original = {
      httpRequest: { path: '/proxy' },
      httpForward: { scheme: 'HTTPS', host: 'upstream', port: 443 },
      scenarioName: 'flow',
      id: 'fwd',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'fwd', path: '/proxy' }),
      // quickActionModeled === false for a non-httpResponse recognised original.
      { type: 'static', static: { statusCode: 200, body: '', contentType: '' }, editOriginal: original, editActionModeled: false },
    );
    expect(wire['httpForward']).toEqual(original.httpForward);
    expect(wire['httpResponse']).toBeUndefined();
    expect(wire['scenarioName']).toBe('flow');
    // The forward would be surfaced in the Quick "Preserving …" chip.
    expect(unmodeledFieldNames(original, { actionModeled: false })).toContain('httpForward');
  });

  it('Quick-mock update on an UNMODELED-action original (response sequence) preserves it rather than replacing it with a static response', () => {
    // If the original action is not a recognisable static response, ComposerView
    // records editActionModeled=false, so the Quick static default must NOT clobber
    // the sequence (prefer preserving — consistent with the Advanced bias).
    const original = {
      httpRequest: { path: '/poll' },
      httpResponses: [{ statusCode: 202 }, { statusCode: 200 }],
      responseMode: 'SEQUENTIAL',
      id: 'poll-seq',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'poll-seq', path: '/poll' }),
      { type: 'static', static: { statusCode: 200, body: '', contentType: '' }, editOriginal: original, editActionModeled: false },
    );
    expect(wire['httpResponses']).toEqual(original.httpResponses);
    expect(wire['responseMode']).toBe('SEQUENTIAL');
    expect(wire['httpResponse']).toBeUndefined();
  });

  it('new-compose output (no editOriginal) contains no passthrough artifacts', () => {
    const json = buildExpectationJson(
      baseMatcher({ path: '/fresh', method: 'GET' }),
      staticAction(undefined, 200),
    );
    // Only the keys the form itself emits — nothing merged in.
    expect(Object.keys(json).sort()).toEqual(['httpRequest', 'httpResponse'].sort());
    expect(json['scenarioName']).toBeUndefined();
    expect(json['httpResponses']).toBeUndefined();
    expect(json['crossProtocolScenarios']).toBeUndefined();
    // The internal overlay fields never leak into the payload.
    expect(json['editOriginal']).toBeUndefined();
    expect(json['editActionModeled']).toBeUndefined();
  });
});

// ---------------------------------------------------------------------------
// Default-preserving keys (priority / times / timeToLive) — an unedited edit of
// an expectation that carries the server's EXPLICIT default forms must round-trip
// to an EMPTY diff, not spuriously delete those keys. A genuine reset of a real
// non-default value stays a visible change.
// ---------------------------------------------------------------------------

describe('priority / times / timeToLive — default forms preserved on an untouched edit', () => {
  it('round-trips an expectation carrying explicit-default priority/times/timeToLive + an unmodeled action + scenarioName to a ZERO diff (the screenshot case)', () => {
    // Exactly the shape from the bug report: an LLM expectation whose action the
    // form cannot model, carrying the server's explicit default forms.
    const original = {
      httpRequest: { method: 'POST', path: '/v1/chat/completions' },
      httpLlmResponse: { provider: 'openai', model: 'gpt-4o' },
      scenarioName: 'llm-flow',
      priority: 0,
      times: { unlimited: true },
      timeToLive: { unlimited: true },
      id: 'llm-1',
    };
    // The user changes nothing. The form prefills priority 0 / times 0 / ttl 0,
    // and the action is unmodeled (editActionModeled false), so the merge must
    // preserve every field verbatim.
    const wire = buildExpectationJson(
      baseMatcher({ id: 'llm-1', method: 'POST', path: '/v1/chat/completions' }),
      { type: 'static', static: { statusCode: 200, body: '', contentType: '' }, editOriginal: original, editActionModeled: false },
    );
    expect(wire).toEqual(original);
    // No fabricated static response leaked in from the (unmodeled) form action.
    expect(wire['httpResponse']).toBeUndefined();
  });

  it('preserves an explicit-default priority:0 even when the action IS modeled and only the status code changes', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200 },
      priority: 0,
      times: { unlimited: true },
      id: 'p0',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'p0', path: '/api' }),
      staticAction({ editOriginal: original, editActionModeled: true }, 201),
    );
    expect((wire['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(201);
    // The explicit defaults are NOT stripped — no noisy diff for a semantic no-op.
    expect(wire['priority']).toBe(0);
    expect(wire['times']).toEqual({ unlimited: true });
  });

  it('resetting a real non-default times to 0 (unlimited) emits the explicit {unlimited:true} form, not a silent drop', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200 },
      times: { remainingTimes: 4, unlimited: false },
      id: 't',
    };
    // matcher.times defaults to 0 → the user reset Times to unlimited.
    const wire = buildExpectationJson(
      baseMatcher({ id: 't', path: '/api' }),
      staticAction({ editOriginal: original, editActionModeled: true }),
    );
    expect(wire['times']).toEqual({ unlimited: true });
  });

  it('resetting a real non-default timeToLive to forever emits the explicit {unlimited:true} form', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200 },
      timeToLive: { timeUnit: 'SECONDS', timeToLive: 90, unlimited: false },
      id: 'ttl',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'ttl', path: '/api' }),
      staticAction({ editOriginal: original, editActionModeled: true }),
    );
    expect(wire['timeToLive']).toEqual({ unlimited: true });
  });

  it('still deletes a real non-default priority on reset (priority 0 ≡ absent to the server)', () => {
    // Complements the existing "clearing priority deletes it" test: this asserts
    // the reset shape when threaded through buildExpectationJson.
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200 },
      priority: 10,
      id: 'p',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'p', path: '/api' }),
      staticAction({ editOriginal: original, editActionModeled: true }),
    );
    expect(wire['priority']).toBeUndefined();
  });

  it('changing a non-default priority to another non-default value stays form-authoritative', () => {
    const original = {
      httpRequest: { path: '/api' },
      httpResponse: { statusCode: 200 },
      priority: 10,
      id: 'p',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'p', path: '/api', priority: 20 }),
      staticAction({ editOriginal: original, editActionModeled: true }),
    );
    expect(wire['priority']).toBe(20);
  });
});

// ---------------------------------------------------------------------------
// Advanced Scenario section — the three scenario keys become form-MODELED only
// when scenarioModeled is set (the Advanced path). The Quick path (no
// scenarioModeled) keeps them as unmodeled passthrough, so bindings survive.
// ---------------------------------------------------------------------------

describe('scenario bindings — form-modeled only on the Advanced path (scenarioModeled)', () => {
  const scenarioBoundOriginal = {
    httpRequest: { method: 'POST', path: '/checkout' },
    httpResponse: { statusCode: 200, body: 'ok' },
    scenarioName: 'checkout',
    scenarioState: 'PAID',
    newScenarioState: 'SHIPPED',
    namespace: 'orders',
    id: 'checkout-paid',
  };

  it('Advanced edit that changes only the status code round-trips the bindings identically', () => {
    // The Advanced form prefilled the Scenario section from the original, so it
    // re-emits the same three values; scenarioModeled makes them authoritative.
    const wire = buildExpectationJson(
      baseMatcher({ id: 'checkout-paid', method: 'POST', path: '/checkout' }),
      staticAction({
        static: { statusCode: 201, body: 'ok', contentType: '' },
        editOriginal: scenarioBoundOriginal,
        editActionModeled: true,
        scenario: { name: 'checkout', requiredState: 'PAID', transitionTo: 'SHIPPED' },
        scenarioModeled: true,
      }),
    );
    expect((wire['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(201);
    expect(wire['scenarioName']).toBe('checkout');
    expect(wire['scenarioState']).toBe('PAID');
    expect(wire['newScenarioState']).toBe('SHIPPED');
    // Other unmodeled fields still pass through.
    expect(wire['namespace']).toBe('orders');
  });

  it('Advanced edit that clears a scenario field removes that key (form-authoritative)', () => {
    const wire = buildExpectationJson(
      baseMatcher({ id: 'checkout-paid', method: 'POST', path: '/checkout' }),
      staticAction({
        editOriginal: scenarioBoundOriginal,
        editActionModeled: true,
        // User cleared "Transition To" but kept name + required state.
        scenario: { name: 'checkout', requiredState: 'PAID', transitionTo: '' },
        scenarioModeled: true,
      }),
    );
    expect(wire['scenarioName']).toBe('checkout');
    expect(wire['scenarioState']).toBe('PAID');
    expect(wire['newScenarioState']).toBeUndefined();
    // Clearing modeled scenario keys does NOT touch unrelated passthrough.
    expect(wire['namespace']).toBe('orders');
  });

  it('Advanced edit can add a binding to a previously stateless mock', () => {
    const stateless = {
      httpRequest: { method: 'GET', path: '/api' },
      httpResponse: { statusCode: 200 },
      id: 'plain',
    };
    const wire = buildExpectationJson(
      baseMatcher({ id: 'plain', method: 'GET', path: '/api' }),
      staticAction({
        editOriginal: stateless,
        editActionModeled: true,
        scenario: { name: 'flow', requiredState: 'Started', transitionTo: 'next' },
        scenarioModeled: true,
      }),
    );
    expect(wire['scenarioName']).toBe('flow');
    expect(wire['scenarioState']).toBe('Started');
    expect(wire['newScenarioState']).toBe('next');
  });

  it('new Advanced compose with an empty Scenario section is byte-identical to before the section existed', () => {
    const withSection = buildExpectationJson(
      baseMatcher({ path: '/fresh', method: 'GET' }),
      staticAction({ scenario: { name: '', requiredState: '', transitionTo: '' }, scenarioModeled: true }),
    );
    const withoutSection = buildExpectationJson(
      baseMatcher({ path: '/fresh', method: 'GET' }),
      staticAction(),
    );
    expect(withSection).toEqual(withoutSection);
    expect(Object.keys(withSection).sort()).toEqual(['httpRequest', 'httpResponse'].sort());
  });

  it('Quick-path edit (no scenarioModeled) preserves bindings even though the Quick form never renders them', () => {
    // The Quick static "Update mock" action never sets scenario/scenarioModeled;
    // the three keys must survive as unmodeled passthrough.
    const wire = buildExpectationJson(
      baseMatcher({ id: 'checkout-paid', method: 'POST', path: '/checkout' }),
      { type: 'static', static: { statusCode: 201, body: 'ok', contentType: '' }, editOriginal: scenarioBoundOriginal, editActionModeled: true },
    );
    expect(wire['scenarioName']).toBe('checkout');
    expect(wire['scenarioState']).toBe('PAID');
    expect(wire['newScenarioState']).toBe('SHIPPED');
  });
});

// ---------------------------------------------------------------------------
// unmodeledFieldNames — drives the "Preserving N fields …" indicator
// ---------------------------------------------------------------------------

describe('unmodeledFieldNames', () => {
  it('lists top-level and nested request fields the form does not model', () => {
    const original = {
      httpRequest: { method: 'GET', path: '/svc', keepAlive: true, protocol: 'HTTP_2' },
      httpResponse: { statusCode: 200 },
      scenarioName: 'checkout',
      scenarioState: 'PAID',
      rateLimit: { limit: 10, windowMillis: 1000 },
      id: 'z',
      priority: 5,
    };
    const names = unmodeledFieldNames(original, { actionModeled: true });
    expect(names).toContain('scenarioName');
    expect(names).toContain('scenarioState');
    expect(names).toContain('rateLimit');
    expect(names).toContain('httpRequest.keepAlive');
    expect(names).toContain('httpRequest.protocol');
    // Modeled fields are NOT reported.
    expect(names).not.toContain('id');
    expect(names).not.toContain('priority');
    expect(names).not.toContain('httpResponse');
    expect(names).not.toContain('httpRequest.method');
    expect(names).not.toContain('httpRequest.path');
  });

  it('reports a preserved unmodeled action (response sequence) only when actionModeled is false', () => {
    const original = {
      httpRequest: { path: '/poll' },
      httpResponses: [{ statusCode: 200 }],
      responseMode: 'SEQUENTIAL',
    };
    expect(unmodeledFieldNames(original, { actionModeled: false })).toEqual(
      expect.arrayContaining(['httpResponses', 'responseMode']),
    );
    // When the form owns the action slot it will replace these, so they are not
    // reported as "preserved".
    expect(unmodeledFieldNames(original, { actionModeled: true })).not.toContain('httpResponses');
  });

  it('drops the three scenario keys from the preserved list when scenarioModeled (Advanced), keeping other passthrough', () => {
    const original = {
      httpRequest: { path: '/svc' },
      httpResponse: { statusCode: 200 },
      scenarioName: 'checkout',
      scenarioState: 'PAID',
      newScenarioState: 'SHIPPED',
      namespace: 'orders',
      crossProtocolScenarios: [{ trigger: 'DNS_QUERY' }],
    };
    // Advanced path: scenario keys are modeled, so they must NOT be listed…
    const advanced = unmodeledFieldNames(original, { actionModeled: true, scenarioModeled: true });
    expect(advanced).not.toContain('scenarioName');
    expect(advanced).not.toContain('scenarioState');
    expect(advanced).not.toContain('newScenarioState');
    // …but genuinely unmodeled fields still are.
    expect(advanced).toContain('namespace');
    expect(advanced).toContain('crossProtocolScenarios');
    // Quick path (no scenarioModeled): the scenario keys stay in the chip.
    const quick = unmodeledFieldNames(original, { actionModeled: true });
    expect(quick).toContain('scenarioName');
    expect(quick).toContain('scenarioState');
    expect(quick).toContain('newScenarioState');
  });

  it('returns an empty list when the form models everything present', () => {
    const original = {
      httpRequest: { method: 'GET', path: '/api' },
      httpResponse: { statusCode: 200 },
      id: 'a',
    };
    expect(unmodeledFieldNames(original, { actionModeled: true })).toEqual([]);
  });
});

// ---------------------------------------------------------------------------
// JWT — form-modeled on edit ONLY when the form faithfully owns the original
// jwt (jwtModeled). Otherwise the original jwt is preserved as passthrough so a
// lossy prefill can never rewrite an unfaithfully-representable jwt.
// ---------------------------------------------------------------------------

describe('jwtFaithfullyModeled — faithfulness gate', () => {
  it('is true when there is no jwt (form owns the empty slot → added jwt is emitted)', () => {
    expect(jwtFaithfullyModeled({ path: '/api' })).toBe(true);
    expect(jwtFaithfullyModeled(undefined)).toBe(true);
  });

  it('is true for plain-string claim / issuer forms that round-trip losslessly', () => {
    expect(jwtFaithfullyModeled({ path: '/api', jwt: { claims: { sub: 'user-1' }, issuer: 'iss' } })).toBe(true);
    // a plain-string (regex) claim value also round-trips
    expect(jwtFaithfullyModeled({ path: '/api', jwt: { claims: { scope: '.*admin.*' } } })).toBe(true);
  });

  it('is FALSE for an object-form NottableString claim the flat form cannot represent', () => {
    expect(jwtFaithfullyModeled({ path: '/api', jwt: { claims: { sub: { value: 'user-1', not: false, optional: true } } } })).toBe(false);
    expect(jwtFaithfullyModeled({ path: '/api', jwt: { issuer: { value: 'iss', optional: true } } })).toBe(false);
  });
});

describe('mergeUnmodeledFields — jwt (jwtModeled)', () => {
  const formReqWithJwt = { httpRequest: { path: '/api', jwt: { claims: { sub: 'new' } } } };

  it('(a) adds a jwt to a jwt-less original when jwtModeled', () => {
    const original = { httpRequest: { path: '/api' }, httpResponse: { statusCode: 200 } };
    const merged = mergeUnmodeledFields(original, formReqWithJwt, { jwtModeled: true });
    expect((merged['httpRequest'] as Record<string, unknown>)['jwt']).toEqual({ claims: { sub: 'new' } });
  });

  it('(b) modifies an existing jwt when jwtModeled', () => {
    const original = { httpRequest: { path: '/api', jwt: { claims: { sub: 'old' } } }, httpResponse: { statusCode: 200 } };
    const merged = mergeUnmodeledFields(original, formReqWithJwt, { jwtModeled: true });
    expect((merged['httpRequest'] as Record<string, unknown>)['jwt']).toEqual({ claims: { sub: 'new' } });
  });

  it('(c) removes the jwt when the form clears it (jwtModeled, no jwt in formJson)', () => {
    const original = { httpRequest: { path: '/api', jwt: { claims: { sub: 'old' } } }, httpResponse: { statusCode: 200 } };
    const merged = mergeUnmodeledFields(original, { httpRequest: { path: '/api' } }, { jwtModeled: true });
    expect('jwt' in (merged['httpRequest'] as Record<string, unknown>)).toBe(false);
  });

  it('(d) PRESERVES an unfaithful original jwt when NOT jwtModeled (lossy prefill ignored)', () => {
    const originalJwt = { claims: { sub: { value: 'user-1', not: false, optional: true } } };
    const original = { httpRequest: { path: '/api', jwt: originalJwt }, httpResponse: { statusCode: 200 } };
    // The form emits a lossy jwt, but jwtModeled=false → the original survives.
    const merged = mergeUnmodeledFields(original, formReqWithJwt, { jwtModeled: false });
    expect((merged['httpRequest'] as Record<string, unknown>)['jwt']).toEqual(originalJwt);
  });

  it('unmodeledFieldNames reports httpRequest.jwt only when NOT jwtModeled', () => {
    const original = { httpRequest: { path: '/api', jwt: { claims: { sub: 'x' } } } };
    expect(unmodeledFieldNames(original, { jwtModeled: false })).toContain('httpRequest.jwt');
    expect(unmodeledFieldNames(original, { jwtModeled: true })).not.toContain('httpRequest.jwt');
  });

  it('end-to-end: a form JWT edit on a jwt-less original is DROPPED without jwtModeled but KEPT with it', () => {
    const matcher = baseMatcher({ jwt: { claims: 'sub=user-1' } });
    const original = { httpRequest: { path: '/api/test' }, httpResponse: { statusCode: 200 } };
    // Quick-style path (no jwtModeled) → the form jwt is not authoritative, so it is dropped.
    const withoutFlag = buildExpectationJson(matcher, staticAction({ editOriginal: original, editActionModeled: true }));
    expect('jwt' in (withoutFlag['httpRequest'] as Record<string, unknown>)).toBe(false);
    // Advanced path (jwtModeled) → the form jwt is emitted.
    const withFlag = buildExpectationJson(matcher, staticAction({ editOriginal: original, editActionModeled: true, jwtModeled: true }));
    expect((withFlag['httpRequest'] as Record<string, unknown>)['jwt']).toEqual({ claims: { sub: 'user-1' } });
  });
});

describe('metadata — preserved verbatim on edit (never matched, never modeled by the form)', () => {
  // Mirrors test-fixtures/expectations/top_metadata.json: every JSON value kind, including null.
  const metadata = {
    owner: 'team-a',
    ticket: 4211,
    ratio: 0.5,
    flaky: false,
    note: null,
    tags: ['smoke', 'checkout'],
    contact: { team: 'checkout', oncall: ['ann', 'bob'] },
    literal: '!not?optional 100%+a b',
  };
  const original = {
    id: 'metadata-1',
    httpRequest: { method: 'GET', path: '/metadata' },
    httpResponse: { statusCode: 200, body: 'ok' },
    metadata,
  };

  it('an Advanced edit of the status code saves metadata intact', () => {
    const wire = buildExpectationJson(
      baseMatcher({ id: 'metadata-1', path: '/metadata' }),
      staticAction({ editOriginal: original, editActionModeled: true, scenarioModeled: true, jwtModeled: true }, 201),
    );
    expect((wire['httpResponse'] as Record<string, unknown>)['statusCode']).toBe(201);
    expect(wire['metadata']).toEqual(metadata);
    expect(wire['metadata']).not.toBe(metadata);
  });

  it('a Quick-path edit with an unmodeled action saves metadata intact', () => {
    const wire = buildExpectationJson(
      baseMatcher({ id: 'metadata-1', path: '/metadata-moved' }),
      staticAction({ editOriginal: original, editActionModeled: false }),
    );
    expect((wire['httpRequest'] as Record<string, unknown>)['path']).toBe('/metadata-moved');
    expect(wire['metadata']).toEqual(metadata);
  });

  it('is listed among the preserved fields the editor shows', () => {
    expect(unmodeledFieldNames(original)).toContain('metadata');
  });
});
