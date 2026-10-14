/**
 * Tests for the load-scenario code generators — Java, Node.js, Python, Go, C#,
 * Ruby, Rust, JSON, and curl.
 *
 * The client-language snippets build the scenario with each client's FIRST-CLASS
 * load-scenario API (rich LoadScenario / LoadProfile / LoadStage / LoadStep
 * objects + builders) and register/run it via `loadScenario(...)` /
 * `startLoadScenarios(...)`. JSON and curl stay raw wire format (the two PUT
 * bodies): register (PUT /loadScenario) then start (PUT /loadScenario/start).
 *
 * Assertions check that the right builder symbols appear per language and that
 * partial/empty input still produces non-throwing output.
 */
import { describe, it, expect } from 'vitest';
import {
  loadToJava,
  loadToJson,
  loadToCurl,
  loadToNode,
  loadToPython,
  loadToGo,
  loadToCsharp,
  loadToRuby,
  loadToRust,
  type LoadScenarioCodegenInput,
} from '../lib/loadScenarioCodegen';
import type { LoadScenarioDTO } from '../lib/loadScenario';

const BASE_URL = 'http://localhost:1080';

const SCENARIO: LoadScenarioDTO = {
  name: 'checkout-load',
  templateType: 'VELOCITY',
  startDelayMillis: 5000,
  maxRequests: 5000,
  profile: {
    stages: [
      { type: 'VU', startVus: 1, endVus: 10, durationMillis: 30000, curve: 'LINEAR' },
      { type: 'VU', vus: 10, durationMillis: 60000 },
      { type: 'RATE', rate: 50, durationMillis: 30000, maxVus: 20 },
      { type: 'PAUSE', durationMillis: 5000 },
    ],
  },
  steps: [
    {
      name: 'fetch-item',
      request: {
        method: 'GET',
        path: '/api/item',
        headers: { Accept: ['application/json'] },
        socketAddress: { host: 'target.svc', port: 8080, scheme: 'HTTP' },
      },
      thinkTime: { timeUnit: 'MILLISECONDS', value: 250 },
    },
  ],
};

const INPUT: LoadScenarioCodegenInput = { scenario: SCENARIO, baseUrl: BASE_URL };

describe('loadScenarioCodegen', () => {
  describe('loadToJson — raw wire format (unchanged)', () => {
    it('emits both the register body and the start body', () => {
      const out = loadToJson(INPUT);
      expect(out).toContain('/mockserver/loadScenario');
      expect(out).toContain('/mockserver/loadScenario/start');
      expect(out).toContain('"name": "checkout-load"');
      expect(out).toContain('"startDelayMillis": 5000');
      // The start body is the {"name": ...} shape.
      const startBodyIndex = out.indexOf('/start');
      expect(out.slice(startBodyIndex)).toContain('"name": "checkout-load"');
    });
  });

  describe('loadToCurl — raw wire format (unchanged)', () => {
    it('emits two PUT commands hitting register then start', () => {
      const out = loadToCurl(INPUT);
      expect(out).toContain(`curl -v -X PUT '${BASE_URL}/mockserver/loadScenario'`);
      expect(out).toContain(`curl -v -X PUT '${BASE_URL}/mockserver/loadScenario/start'`);
      expect(out).toContain(`{"name":"checkout-load"}`);
    });

    it('shell-escapes single quotes in the JSON body', () => {
      const tricky: LoadScenarioCodegenInput = {
        baseUrl: BASE_URL,
        scenario: { ...SCENARIO, steps: [{ request: { path: "/it's", socketAddress: { host: 'h', port: 80 } } }] },
      };
      const out = loadToCurl(tricky);
      expect(out).toContain(`'\\''`);
    });
  });

  // --- Per-language rich-object output ---

  describe('Java — org.mockserver.load.* builders', () => {
    const out = loadToJava(INPUT);
    it('uses MockServerClient + the loadScenario/startLoadScenarios API', () => {
      expect(out).toContain('new MockServerClient("localhost", 1080)');
      expect(out).toContain('loadScenario("checkout-load")');
      expect(out).toContain('client.loadScenario(scenario);');
      expect(out).toContain('client.startLoadScenarios("checkout-load");');
    });
    it('builds the profile with LoadProfile.of + LoadStage factories', () => {
      expect(out).toContain('LoadProfile.of(');
      expect(out).toContain('LoadStage.rampVus(1, 10, 30000L, RampCurve.LINEAR)');
      expect(out).toContain('LoadStage.constantVus(10, 60000L)');
      expect(out).toContain('LoadStage.constantRate(50, 30000L)');
      expect(out).toContain('LoadStage.pause(5000L)');
    });
    it('builds steps with loadStep(request(...)) + Delay.milliseconds', () => {
      expect(out).toContain('loadStep(');
      expect(out).toContain('request()');
      expect(out).toContain('.withMethod("GET")');
      expect(out).toContain('.withPath("/api/item")');
      expect(out).toContain('.withSocketAddress("target.svc", 8080, SocketAddress.Scheme.HTTP)');
      expect(out).toContain('.withThinkTime(Delay.milliseconds(250))');
    });
    it('emits maxRequests / startDelayMillis only when set', () => {
      expect(out).toContain('.withMaxRequests(5000)');
      expect(out).toContain('.withStartDelayMillis(5000L)');
    });
  });

  describe('Node — mockserver-client object + loadScenario/startLoadScenarios', () => {
    const out = loadToNode(INPUT);
    it('constructs the client and calls loadScenario then startLoadScenarios', () => {
      expect(out).toContain("const { mockServerClient } = require('mockserver-client');");
      expect(out).toContain('mockServerClient("localhost", 1080)');
      expect(out).toContain('client.loadScenario(scenario)');
      expect(out).toContain('client.startLoadScenarios(["checkout-load"])');
    });
    it('builds a scenario object literal with profile.stages + steps', () => {
      expect(out).toContain('const scenario = {');
      expect(out).toContain('type: "VU"');
      expect(out).toContain('type: "RATE"');
      expect(out).toContain('type: "PAUSE"');
      expect(out).toContain('socketAddress:');
    });
  });

  describe('Python — mockserver LoadScenario / load_scenario', () => {
    const out = loadToPython(INPUT);
    it('imports and constructs the rich models', () => {
      expect(out).toContain('from mockserver import (');
      expect(out).toContain('LoadScenario(');
      expect(out).toContain('LoadProfile(stages=[');
      expect(out).toContain('LoadStep(request=HttpRequest(');
      expect(out).toContain('SocketAddress(host="target.svc", port=8080, scheme="HTTP")');
    });
    it('uses the LoadStage factory methods', () => {
      expect(out).toContain('LoadStage.vu_stage(30000, start_vus=1, end_vus=10');
      expect(out).toContain('LoadStage.vu_stage(60000, vus=10)');
      expect(out).toContain('LoadStage.rate_stage(30000, rate=50, max_vus=20)');
      expect(out).toContain('LoadStage.pause_stage(5000)');
    });
    it('registers + starts via the client', () => {
      expect(out).toContain('client.load_scenario(scenario)');
      expect(out).toContain('client.start_load_scenarios("checkout-load")');
    });
  });

  describe('Go — struct literals + stage helpers', () => {
    const out = loadToGo(INPUT);
    it('builds a mockserver.LoadScenario struct', () => {
      expect(out).toContain('mockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"');
      expect(out).toContain('mockserver.LoadScenario{');
      expect(out).toContain('Profile: &mockserver.LoadProfile{');
      expect(out).toContain('Steps: []mockserver.LoadStep{');
      expect(out).toContain('SocketAddress: &mockserver.SocketAddress{Host: "target.svc", Port: 8080, Scheme: "HTTP"}');
    });
    it('uses the stage helper functions + ramp curve constant', () => {
      expect(out).toContain('mockserver.RampVusStage(1, 10, 30000, mockserver.RampLinear)');
      expect(out).toContain('mockserver.ConstantVusStage(10, 60000)');
      expect(out).toContain('mockserver.ConstantRateStage(50, 30000)');
      expect(out).toContain('mockserver.PauseStage(5000)');
    });
    it('registers + starts via the client', () => {
      expect(out).toContain('client.LoadScenario(scenario)');
      expect(out).toContain('client.StartLoadScenarios("checkout-load")');
    });
  });

  describe('C# — object initializers + LoadStage factories', () => {
    const out = loadToCsharp(INPUT);
    it('builds a new LoadScenario { ... }', () => {
      expect(out).toContain('using MockServer.Client;');
      expect(out).toContain('new LoadScenario');
      // VELOCITY is the default templateType → omitted from output.
      expect(out).not.toContain('TemplateType =');
    });
    it('uses the LoadStage static factories + RampCurve enum', () => {
      expect(out).toContain('LoadStage.RampVus(1, 10, 30000, RampCurve.LINEAR)');
      expect(out).toContain('LoadStage.ConstantVus(10, 60000)');
      expect(out).toContain('LoadStage.ConstantRate(50, 30000)');
      expect(out).toContain('LoadStage.Pause(5000)');
    });
    it('builds the request via HttpRequest.Request()....Build()', () => {
      expect(out).toContain('HttpRequest.Request().WithMethod("GET").WithPath("/api/item")');
      expect(out).toContain('.Build()');
      expect(out).toContain('ThinkTime = new Delay { TimeUnit = TimeUnit.MILLISECONDS, Value = 250 }');
    });
    it('registers + starts via the client', () => {
      expect(out).toContain('client.LoadScenario(scenario);');
      expect(out).toContain('client.StartLoadScenarios("checkout-load");');
    });
  });

  describe('Ruby — MockServer::* models', () => {
    const out = loadToRuby(INPUT);
    it('builds MockServer::LoadScenario + LoadProfile + LoadStep', () => {
      expect(out).toContain("require 'mockserver-client'");
      expect(out).toContain('MockServer::LoadScenario.new(');
      expect(out).toContain('MockServer::LoadProfile.new(stages: [');
      expect(out).toContain('MockServer::LoadStep.new(');
      expect(out).toContain("MockServer::HttpRequest.new(method: 'GET', path: '/api/item'");
      expect(out).toContain("MockServer::SocketAddress.new(host: 'target.svc', port: 8080, scheme: 'HTTP')");
    });
    it('uses the LoadStage factory methods', () => {
      expect(out).toContain('MockServer::LoadStage.vu(30000, start_vus: 1, end_vus: 10');
      expect(out).toContain('MockServer::LoadStage.vu(60000, vus: 10)');
      expect(out).toContain('MockServer::LoadStage.rate(30000, rate: 50, max_vus: 20)');
      expect(out).toContain('MockServer::LoadStage.pause(5000)');
    });
    it('registers + starts via the client', () => {
      expect(out).toContain('client.load_scenario(scenario)');
      expect(out).toContain("client.start_load_scenarios('checkout-load')");
    });
  });

  describe('Rust — mockserver_client builders', () => {
    const out = loadToRust(INPUT);
    it('builds the scenario with LoadScenario::new + LoadProfile::of', () => {
      expect(out).toContain('use mockserver_client::{');
      expect(out).toContain('ClientBuilder::new("localhost", 1080).build()?');
      expect(out).toContain('LoadProfile::of(vec![');
      expect(out).toContain('LoadScenario::new("checkout-load", profile, steps)');
    });
    it('uses the LoadStage associated functions + RampCurve variants', () => {
      expect(out).toContain('LoadStage::vu_ramp(1, 10, 30000, RampCurve::Linear)');
      expect(out).toContain('LoadStage::vu_hold(10, 60000)');
      expect(out).toContain('LoadStage::rate_hold(50.0, 30000).max_vus(20)');
      expect(out).toContain('LoadStage::pause(5000)');
    });
    it('builds steps + request builder + Delay::milliseconds', () => {
      expect(out).toContain('LoadStep::new(');
      expect(out).toContain('HttpRequest::new()');
      expect(out).toContain('.method("GET")');
      expect(out).toContain('.socket_address(SocketAddress::new("target.svc", 8080).scheme("HTTP"))');
      expect(out).toContain('.think_time(Delay::milliseconds(250))');
    });
    it('registers + starts via the client', () => {
      expect(out).toContain('client.load_scenario(&scenario)?;');
      expect(out).toContain('client.start_load_scenarios(&["checkout-load"])?;');
    });
  });

  // --- Robustness: every emitter tolerates partial / empty input ---

  const langs: Array<[string, (i: LoadScenarioCodegenInput) => string]> = [
    ['Java', loadToJava],
    ['Node', loadToNode],
    ['Python', loadToPython],
    ['Go', loadToGo],
    ['C#', loadToCsharp],
    ['Ruby', loadToRuby],
    ['Rust', loadToRust],
    ['JSON', loadToJson],
    ['curl', loadToCurl],
  ];

  it.each(langs)('%s tolerates an essentially-empty scenario without throwing', (_label, gen) => {
    const empty: LoadScenarioCodegenInput = {
      baseUrl: BASE_URL,
      scenario: { name: '', profile: { stages: [] }, steps: [] },
    };
    expect(() => gen(empty)).not.toThrow();
    const out = gen(empty);
    expect(out.length).toBeGreaterThan(0);
  });

  it.each(langs)('%s tolerates a partial scenario (no host/path, no stage values)', (_label, gen) => {
    const partial: LoadScenarioCodegenInput = {
      baseUrl: BASE_URL,
      scenario: {
        name: 'wip',
        profile: { stages: [{ type: 'VU', durationMillis: 0 }, { type: 'PAUSE', durationMillis: 1000 }] },
        steps: [{ request: {} }, { request: { path: '/only-path' } }],
      },
    };
    expect(() => gen(partial)).not.toThrow();
    expect(gen(partial)).toContain('wip');
  });
});
