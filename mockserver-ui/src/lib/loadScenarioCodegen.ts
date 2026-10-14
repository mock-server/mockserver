/**
 * Codegen for MockServer Load Scenarios — emits snippets that REGISTER the
 * current authored scenario and then START it, for the raw JSON wire bodies,
 * curl, and the client languages the Composer/Verification panels already
 * support (Java, Node.js, Python, Go, C#, Ruby, Rust).
 *
 * Every client library ships a first-class load-scenario API — rich
 * LoadScenario / LoadProfile / LoadStage / LoadStep types plus
 * `loadScenario(...)` (register) and `startLoadScenarios(...)` (run) methods —
 * so the client emitters build those idiomatic objects/builders rather than
 * PUTting raw JSON over a generic HTTP client. Only {@link loadToJson} and
 * {@link loadToCurl} stay raw wire format (those two ARE the JSON contract:
 *   1. PUT  /mockserver/loadScenario        (body = the LoadScenario JSON) — register
 *   2. PUT  /mockserver/loadScenario/start   (body = {"name": "<name>"})   — run
 * ).
 *
 * Host/port parsing and the Python/Rust literal helpers are reused from
 * standardCodegen.ts so the output is consistent with the other panels.
 *
 * Robustness: every emitter (and {@link LoadScenarioCodegenInput.scenario}
 * itself) tolerates a partially-filled scenario — a missing name, no steps, no
 * stages, a step with no host/path, a stage with no values — so the UI can
 * render a growing builder chain field-by-field as the user types. Optional
 * fields are omitted at their defaults (templateType VELOCITY, unset
 * maxRequests/startDelayMillis, zero/absent thinkTime, LINEAR curve on hold
 * stages, etc.).
 */

import { clientHostPort, toPythonLiteral, rustClientVersionRequirement } from './standardCodegen';
import type {
  LoadScenarioDTO,
  LoadStageDTO,
  LoadStepDTO,
  LoadRequestDTO,
  DelayDTO,
} from './loadScenario';

export interface LoadScenarioCodegenInput {
  /** The scenario to register — may be partial while the author form is incomplete. */
  scenario: LoadScenarioDTO;
  /** MockServer base URL for the REST calls. */
  baseUrl: string;
}

const REGISTER_PATH = '/mockserver/loadScenario';
const START_PATH = '/mockserver/loadScenario/start';

/** The {"name": "<name>"} body the /start endpoint takes for a single scenario. */
function startBody(name: string): Record<string, string> {
  return { name };
}

/** A non-blank scenario name, or a sensible placeholder so partial output still reads cleanly. */
function nameOr(scenario: LoadScenarioDTO, fallback = 'load-scenario'): string {
  const n = (scenario.name ?? '').trim();
  return n !== '' ? n : fallback;
}

/** Whether templateType should be emitted (anything other than the VELOCITY default). */
function hasNonDefaultTemplate(scenario: LoadScenarioDTO): boolean {
  return scenario.templateType != null && scenario.templateType !== 'VELOCITY';
}

/** The stages of the profile, tolerating an absent/empty profile. */
function stagesOf(scenario: LoadScenarioDTO): LoadStageDTO[] {
  return scenario.profile?.stages ?? [];
}

/** Classify a stage so each emitter renders the right hold/ramp/pause variant. */
type StageKind = 'vu-hold' | 'vu-ramp' | 'rate-hold' | 'rate-ramp' | 'pause';
function stageKind(stage: LoadStageDTO): StageKind {
  if (stage.type === 'PAUSE') return 'pause';
  if (stage.type === 'RATE') {
    return stage.startRate != null && stage.endRate != null ? 'rate-ramp' : 'rate-hold';
  }
  return stage.startVus != null && stage.endVus != null ? 'vu-ramp' : 'vu-hold';
}

const num = (v: number | undefined, fallback = 0): number => (v != null && Number.isFinite(v) ? v : fallback);

// ---------------------------------------------------------------------------
// JSON output — the two raw PUT bodies, clearly labelled.
// ---------------------------------------------------------------------------

export function loadToJson(input: LoadScenarioCodegenInput): string {
  const register = JSON.stringify(input.scenario, null, 2);
  const start = JSON.stringify(startBody(nameOr(input.scenario)), null, 2);
  return [
    `// 1. Register — PUT ${REGISTER_PATH}`,
    register,
    '',
    `// 2. Start — PUT ${START_PATH}`,
    start,
  ].join('\n');
}

// ---------------------------------------------------------------------------
// curl output — two PUT commands.
// ---------------------------------------------------------------------------

function shellSafe(json: string): string {
  return json.replace(/'/g, `'\\''`);
}

export function loadToCurl(input: LoadScenarioCodegenInput): string {
  const register = shellSafe(JSON.stringify(input.scenario));
  const start = shellSafe(JSON.stringify(startBody(nameOr(input.scenario))));
  return [
    `# 1. Register the scenario`,
    `curl -v -X PUT '${input.baseUrl}${REGISTER_PATH}' \\`,
    `  -H 'Content-Type: application/json' \\`,
    `  -d '${register}'`,
    '',
    `# 2. Start it (requires loadGenerationEnabled=true)`,
    `curl -v -X PUT '${input.baseUrl}${START_PATH}' \\`,
    `  -H 'Content-Type: application/json' \\`,
    `  -d '${start}'`,
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Java — MockServerClient + the rich org.mockserver.load.* builder API.
// LoadScenario.loadScenario(name).withProfile(LoadProfile.of(stages...)).withSteps(steps...)
// then client.loadScenario(scenario) + client.startLoadScenarios(name).
// ---------------------------------------------------------------------------

const javaStr = (s: string) => s.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t');

/** Java `LoadStage.<factory>(...)` for one stage. */
function javaStage(stage: LoadStageDTO): string {
  const dur = num(stage.durationMillis);
  const curve = `RampCurve.${stage.curve ?? 'LINEAR'}`;
  switch (stageKind(stage)) {
    case 'vu-hold':
      return `LoadStage.constantVus(${num(stage.vus)}, ${dur}L)`;
    case 'vu-ramp':
      return `LoadStage.rampVus(${num(stage.startVus)}, ${num(stage.endVus)}, ${dur}L, ${curve})`;
    case 'rate-hold':
      return `LoadStage.constantRate(${num(stage.rate)}, ${dur}L)`;
    case 'rate-ramp':
      return `LoadStage.rampRate(${num(stage.startRate)}, ${num(stage.endRate)}, ${dur}L, ${curve})`;
    case 'pause':
      return `LoadStage.pause(${dur}L)`;
  }
}

/** Java `request()....withSocketAddress(...)` for a step's request, as indented builder lines. */
function javaRequest(request: LoadRequestDTO, indent: string): string {
  const lines: string[] = ['request()'];
  if (request.method) lines.push(`${indent}    .withMethod("${javaStr(request.method)}")`);
  if (request.path) lines.push(`${indent}    .withPath("${javaStr(request.path)}")`);
  if (request.headers) {
    for (const [k, vs] of Object.entries(request.headers)) {
      for (const v of vs) lines.push(`${indent}    .withHeader("${javaStr(k)}", "${javaStr(v)}")`);
    }
  }
  if (request.body != null && request.body !== '') lines.push(`${indent}    .withBody("${javaStr(request.body)}")`);
  const sa = request.socketAddress;
  if (sa && sa.host) {
    lines.push(`${indent}    .withSocketAddress("${javaStr(sa.host)}", ${num(sa.port)}, SocketAddress.Scheme.${sa.scheme ?? 'HTTP'})`);
  }
  return lines.join('\n');
}

/** Java `loadStep(request(...)).withThinkTime(...)` for one step. */
function javaStep(step: LoadStepDTO): string {
  const lines = ['loadStep('];
  lines.push(`        ${javaRequest(step.request ?? {}, '        ')}`);
  lines.push('    )');
  if (step.name) lines.push(`    .withName("${javaStr(step.name)}")`);
  if (step.thinkTime && num(step.thinkTime.value) > 0) {
    lines.push(`    .withThinkTime(Delay.milliseconds(${num(step.thinkTime.value)}))`);
  }
  return lines.join('\n');
}

export function loadToJava(input: LoadScenarioCodegenInput): string {
  const { scenario } = input;
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(scenario);
  const stages = stagesOf(scenario);
  const steps = scenario.steps ?? [];

  const scenarioLines: string[] = [`LoadScenario scenario = loadScenario("${javaStr(name)}")`];
  if (hasNonDefaultTemplate(scenario)) {
    scenarioLines.push(`    .withTemplateType(HttpTemplate.TemplateType.${scenario.templateType})`);
  }
  if (scenario.maxRequests != null) scenarioLines.push(`    .withMaxRequests(${num(scenario.maxRequests)})`);
  if (scenario.startDelayMillis != null) scenarioLines.push(`    .withStartDelayMillis(${num(scenario.startDelayMillis)}L)`);
  if (scenario.labels) {
    for (const [k, v] of Object.entries(scenario.labels)) {
      scenarioLines.push(`    .withLabel("${javaStr(k)}", "${javaStr(v)}")`);
    }
  }
  // Profile — LoadProfile.of(stage, stage, ...)
  if (stages.length > 0) {
    const stageExprs = stages.map(javaStage);
    scenarioLines.push('    .withProfile(LoadProfile.of(');
    stageExprs.forEach((expr, i) => scenarioLines.push(`        ${expr}${i < stageExprs.length - 1 ? ',' : ''}`));
    scenarioLines.push('    ))');
  }
  // Steps — withSteps(loadStep(...), loadStep(...))
  if (steps.length > 0) {
    scenarioLines.push('    .withSteps(');
    steps.forEach((step, i) => {
      const stepExpr = javaStep(step);
      const indented = stepExpr.split('\n').map((l) => `        ${l}`).join('\n');
      scenarioLines.push(`${indented}${i < steps.length - 1 ? ',' : ''}`);
    });
    scenarioLines.push('    )');
  }
  scenarioLines[scenarioLines.length - 1] += ';';

  return [
    'import org.mockserver.client.MockServerClient;',
    'import org.mockserver.load.LoadScenario;',
    'import org.mockserver.load.LoadProfile;',
    'import org.mockserver.load.LoadStage;',
    'import org.mockserver.load.LoadStep;',
    'import org.mockserver.load.RampCurve;',
    'import org.mockserver.model.Delay;',
    'import org.mockserver.model.HttpTemplate;',
    'import org.mockserver.model.SocketAddress;',
    'import static org.mockserver.load.LoadScenario.loadScenario;',
    'import static org.mockserver.load.LoadStep.loadStep;',
    'import static org.mockserver.model.HttpRequest.request;',
    '',
    `MockServerClient client = new MockServerClient("${host}", ${port});`,
    '',
    '// 1. Build & register the scenario',
    ...scenarioLines,
    'client.loadScenario(scenario);',
    '',
    '// 2. Start it (requires loadGenerationEnabled=true)',
    `client.startLoadScenarios("${javaStr(name)}");`,
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Node.js — mockserver-client. The scenario is a plain object literal; the
// client's loadScenario(scenario) registers it and startLoadScenarios([name])
// runs it. Both return promises, chained with .then(...).
// ---------------------------------------------------------------------------

/** Render a JS object literal at the given indentation (omitting undefined values). */
function jsLiteral(value: unknown, indent: number): string {
  const pad = ' '.repeat(indent);
  const pad2 = ' '.repeat(indent + 2);
  if (value === null || value === undefined) return 'null';
  if (typeof value === 'string') return JSON.stringify(value);
  if (typeof value === 'number' || typeof value === 'boolean') return String(value);
  if (Array.isArray(value)) {
    if (value.length === 0) return '[]';
    return '[\n' + value.map((v) => pad2 + jsLiteral(v, indent + 2)).join(',\n') + '\n' + pad + ']';
  }
  const entries = Object.entries(value as Record<string, unknown>).filter(([, v]) => v !== undefined);
  if (entries.length === 0) return '{}';
  return '{\n' + entries.map(([k, v]) => pad2 + k + ': ' + jsLiteral(v, indent + 2)).join(',\n') + '\n' + pad + '}';
}

/** Strip undefined values and build a clean plain-object scenario for the Node client literal. */
function nodeScenarioObject(scenario: LoadScenarioDTO): Record<string, unknown> {
  const out: Record<string, unknown> = { name: nameOr(scenario) };
  if (hasNonDefaultTemplate(scenario)) out.templateType = scenario.templateType;
  if (scenario.maxRequests != null) out.maxRequests = scenario.maxRequests;
  if (scenario.startDelayMillis != null) out.startDelayMillis = scenario.startDelayMillis;
  if (scenario.labels) out.labels = scenario.labels;
  out.profile = { stages: stagesOf(scenario).map(nodeStage) };
  out.steps = (scenario.steps ?? []).map(nodeStep);
  return out;
}

function nodeStage(stage: LoadStageDTO): Record<string, unknown> {
  const out: Record<string, unknown> = { type: stage.type, durationMillis: num(stage.durationMillis) };
  switch (stageKind(stage)) {
    case 'vu-hold':
      out.vus = num(stage.vus);
      break;
    case 'vu-ramp':
      out.startVus = num(stage.startVus);
      out.endVus = num(stage.endVus);
      if (stage.curve && stage.curve !== 'LINEAR') out.curve = stage.curve;
      break;
    case 'rate-hold':
      out.rate = num(stage.rate);
      break;
    case 'rate-ramp':
      out.startRate = num(stage.startRate);
      out.endRate = num(stage.endRate);
      if (stage.curve && stage.curve !== 'LINEAR') out.curve = stage.curve;
      break;
    case 'pause':
      break;
  }
  if (stage.maxVus != null) out.maxVus = stage.maxVus;
  return out;
}

function nodeStep(step: LoadStepDTO): Record<string, unknown> {
  const out: Record<string, unknown> = { request: nodeRequest(step.request ?? {}) };
  if (step.name) out.name = step.name;
  if (step.thinkTime && num(step.thinkTime.value) > 0) out.thinkTime = thinkTimeObject(step.thinkTime);
  return out;
}

function nodeRequest(request: LoadRequestDTO): Record<string, unknown> {
  const out: Record<string, unknown> = {};
  if (request.method) out.method = request.method;
  if (request.path) out.path = request.path;
  if (request.headers) out.headers = request.headers;
  if (request.body != null && request.body !== '') out.body = request.body;
  const sa = request.socketAddress;
  if (sa && sa.host) {
    out.socketAddress = { host: sa.host, port: num(sa.port), scheme: sa.scheme ?? 'HTTP' };
  }
  return out;
}

function thinkTimeObject(delay: DelayDTO): Record<string, unknown> {
  return { timeUnit: delay.timeUnit ?? 'MILLISECONDS', value: num(delay.value) };
}

export function loadToNode(input: LoadScenarioCodegenInput): string {
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(input.scenario);
  const literal = jsLiteral(nodeScenarioObject(input.scenario), 0);
  return [
    "const { mockServerClient } = require('mockserver-client');",
    '',
    `const client = mockServerClient("${host}", ${port});`,
    '',
    `const scenario = ${literal};`,
    '',
    '// 1. Register the scenario, then 2. start it (requires loadGenerationEnabled=true)',
    'client.loadScenario(scenario)',
    `  .then(() => client.startLoadScenarios(["${name}"]))`,
    '  .then(() => console.log("load scenario started"))',
    '  .catch((error) => console.error(error));',
  ].join('\n');
}

// ---------------------------------------------------------------------------
// Python — mockserver client. LoadScenario(...) + LoadProfile(stages=[...]) +
// LoadStage.vu_stage/rate_stage/pause_stage + LoadStep(request=HttpRequest(...)).
// client.load_scenario(scenario) registers; client.start_load_scenarios(name) runs.
// NB: there is no top-level `request` helper — use HttpRequest(...).
// ---------------------------------------------------------------------------

/** Python `LoadStage.<factory>(...)` for one stage. */
function pythonStage(stage: LoadStageDTO): string {
  const dur = num(stage.durationMillis);
  const curve = stage.curve && stage.curve !== 'LINEAR' ? `, curve="${stage.curve}"` : '';
  switch (stageKind(stage)) {
    case 'vu-hold':
      return `LoadStage.vu_stage(${dur}, vus=${num(stage.vus)})`;
    case 'vu-ramp':
      return `LoadStage.vu_stage(${dur}, start_vus=${num(stage.startVus)}, end_vus=${num(stage.endVus)}${curve})`;
    case 'rate-hold': {
      const maxVus = stage.maxVus != null ? `, max_vus=${stage.maxVus}` : '';
      return `LoadStage.rate_stage(${dur}, rate=${num(stage.rate)}${maxVus})`;
    }
    case 'rate-ramp': {
      const maxVus = stage.maxVus != null ? `, max_vus=${stage.maxVus}` : '';
      return `LoadStage.rate_stage(${dur}, start_rate=${num(stage.startRate)}, end_rate=${num(stage.endRate)}${maxVus}${curve})`;
    }
    case 'pause':
      return `LoadStage.pause_stage(${dur})`;
  }
}

/** Python `HttpRequest(...)` kwargs for a step's request, rendered at the given indent. */
function pythonRequest(request: LoadRequestDTO, indent: string): string {
  const args: string[] = [];
  if (request.method) args.push(`method=${JSON.stringify(request.method)}`);
  if (request.path) args.push(`path=${JSON.stringify(request.path)}`);
  if (request.headers) args.push(`headers=${toPythonLiteral(request.headers, indent.length)}`);
  if (request.body != null && request.body !== '') args.push(`body=${JSON.stringify(request.body)}`);
  const sa = request.socketAddress;
  if (sa && sa.host) {
    args.push(`socket_address=SocketAddress(host=${JSON.stringify(sa.host)}, port=${num(sa.port)}, scheme=${JSON.stringify(sa.scheme ?? 'HTTP')})`);
  }
  if (args.length === 0) return 'HttpRequest()';
  return 'HttpRequest(' + args.join(', ') + ')';
}

function pythonStep(step: LoadStepDTO, indent: string): string {
  const args: string[] = [`request=${pythonRequest(step.request ?? {}, indent)}`];
  if (step.name) args.push(`name=${JSON.stringify(step.name)}`);
  if (step.thinkTime && num(step.thinkTime.value) > 0) {
    args.push(`think_time=Delay("${step.thinkTime.timeUnit ?? 'MILLISECONDS'}", ${num(step.thinkTime.value)})`);
  }
  return 'LoadStep(' + args.join(', ') + ')';
}

export function loadToPython(input: LoadScenarioCodegenInput): string {
  const { scenario } = input;
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(scenario);
  const stages = stagesOf(scenario);
  const steps = scenario.steps ?? [];

  const lines: string[] = [
    'from mockserver import (',
    '    MockServerClient,',
    '    LoadScenario,',
    '    LoadProfile,',
    '    LoadStage,',
    '    LoadStep,',
    '    HttpRequest,',
    '    SocketAddress,',
    '    Delay,',
    ')',
    '',
    `client = MockServerClient("${host}", ${port})`,
    '',
    'scenario = LoadScenario(',
    `    name=${JSON.stringify(name)},`,
  ];
  if (hasNonDefaultTemplate(scenario)) lines.push(`    template_type=${JSON.stringify(scenario.templateType)},`);
  if (scenario.maxRequests != null) lines.push(`    max_requests=${num(scenario.maxRequests)},`);
  if (scenario.startDelayMillis != null) lines.push(`    start_delay_millis=${num(scenario.startDelayMillis)},`);
  if (scenario.labels) lines.push(`    labels=${toPythonLiteral(scenario.labels, 4)},`);
  // profile
  if (stages.length > 0) {
    lines.push('    profile=LoadProfile(stages=[');
    stages.forEach((s) => lines.push(`        ${pythonStage(s)},`));
    lines.push('    ]),');
  } else {
    lines.push('    profile=LoadProfile(stages=[]),');
  }
  // steps
  if (steps.length > 0) {
    lines.push('    steps=[');
    steps.forEach((s) => lines.push(`        ${pythonStep(s, '        ')},`));
    lines.push('    ],');
  } else {
    lines.push('    steps=[],');
  }
  lines.push(')');
  lines.push('');
  lines.push('# 1. Register the scenario');
  lines.push('client.load_scenario(scenario)');
  lines.push('');
  lines.push('# 2. Start it (requires loadGenerationEnabled=true)');
  lines.push(`client.start_load_scenarios("${name}")`);
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Go — struct literals + the stage helper functions, then
// client.LoadScenario(scenario) + client.StartLoadScenarios("name").
// ---------------------------------------------------------------------------

/** Go `mockserver.<helper>(...)` for one stage. */
function goStage(stage: LoadStageDTO): string {
  const dur = num(stage.durationMillis);
  const curve = `mockserver.Ramp${titleCurve(stage.curve)}`;
  switch (stageKind(stage)) {
    case 'vu-hold':
      return `mockserver.ConstantVusStage(${num(stage.vus)}, ${dur})`;
    case 'vu-ramp':
      return `mockserver.RampVusStage(${num(stage.startVus)}, ${num(stage.endVus)}, ${dur}, ${curve})`;
    case 'rate-hold':
      return `mockserver.ConstantRateStage(${num(stage.rate)}, ${dur})`;
    case 'rate-ramp':
      return `mockserver.RampRateStage(${num(stage.startRate)}, ${num(stage.endRate)}, ${dur}, ${curve})`;
    case 'pause':
      return `mockserver.PauseStage(${dur})`;
  }
}

/** RampLinear / RampExponential / RampQuadratic suffix. */
function titleCurve(curve: string | undefined): string {
  switch (curve) {
    case 'EXPONENTIAL':
      return 'Exponential';
    case 'QUADRATIC':
      return 'Quadratic';
    default:
      return 'Linear';
  }
}

const goStr = (s: string) => JSON.stringify(s);

/** Go `map[string][]string{...}` literal for headers. */
function goHeaders(headers: Record<string, string[]>, indent: string): string {
  const entries = Object.entries(headers);
  if (entries.length === 0) return 'map[string][]string{}';
  const lines = ['map[string][]string{'];
  for (const [k, vs] of entries) {
    lines.push(`${indent}\t${goStr(k)}: {${vs.map(goStr).join(', ')}},`);
  }
  lines.push(`${indent}}`);
  return lines.join('\n');
}

/** Go `&mockserver.HttpRequest{...}` for a step's request. */
function goRequest(request: LoadRequestDTO, indent: string): string {
  const fields: string[] = [];
  if (request.method) fields.push(`${indent}\tMethod: ${goStr(request.method)},`);
  if (request.path) fields.push(`${indent}\tPath:   ${goStr(request.path)},`);
  if (request.headers) fields.push(`${indent}\tHeaders: ${goHeaders(request.headers, indent + '\t')},`);
  if (request.body != null && request.body !== '') fields.push(`${indent}\tBody: ${goStr(request.body)},`);
  const sa = request.socketAddress;
  if (sa && sa.host) {
    fields.push(`${indent}\tSocketAddress: &mockserver.SocketAddress{Host: ${goStr(sa.host)}, Port: ${num(sa.port)}, Scheme: ${goStr(sa.scheme ?? 'HTTP')}},`);
  }
  if (fields.length === 0) return '&mockserver.HttpRequest{}';
  return ['&mockserver.HttpRequest{', ...fields, `${indent}}`].join('\n');
}

export function loadToGo(input: LoadScenarioCodegenInput): string {
  const { scenario } = input;
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(scenario);
  const stages = stagesOf(scenario);
  const steps = scenario.steps ?? [];

  const lines: string[] = [
    'package main',
    '',
    'import (',
    '\t"fmt"',
    '',
    '\tmockserver "github.com/mock-server/mockserver-monorepo/mockserver-client-go/v9"',
    ')',
    '',
    'func main() {',
    `\tclient := mockserver.New("${host}", ${port})`,
    '',
    '\tscenario := mockserver.LoadScenario{',
    `\t\tName: ${goStr(name)},`,
  ];
  if (hasNonDefaultTemplate(scenario)) lines.push(`\t\tTemplateType: ${goStr(scenario.templateType!)},`);
  if (scenario.maxRequests != null) lines.push(`\t\tMaxRequests: ${num(scenario.maxRequests)},`);
  if (scenario.startDelayMillis != null) lines.push(`\t\tStartDelayMillis: ${num(scenario.startDelayMillis)},`);
  if (scenario.labels) {
    const entries = Object.entries(scenario.labels);
    lines.push('\t\tLabels: map[string]string{');
    for (const [k, v] of entries) lines.push(`\t\t\t${goStr(k)}: ${goStr(v)},`);
    lines.push('\t\t},');
  }
  // profile
  lines.push('\t\tProfile: &mockserver.LoadProfile{');
  lines.push('\t\t\tStages: []mockserver.LoadStage{');
  stages.forEach((s) => lines.push(`\t\t\t\t${goStage(s)},`));
  lines.push('\t\t\t},');
  lines.push('\t\t},');
  // steps
  lines.push('\t\tSteps: []mockserver.LoadStep{');
  steps.forEach((step) => {
    lines.push('\t\t\t{');
    lines.push(`\t\t\t\tRequest: ${goRequest(step.request ?? {}, '\t\t\t\t')},`);
    if (step.name) lines.push(`\t\t\t\tName: ${goStr(step.name)},`);
    if (step.thinkTime && num(step.thinkTime.value) > 0) {
      lines.push(`\t\t\t\tThinkTime: &mockserver.Delay{TimeUnit: ${goStr(step.thinkTime.timeUnit ?? 'MILLISECONDS')}, Value: ${num(step.thinkTime.value)}},`);
    }
    lines.push('\t\t\t},');
  });
  lines.push('\t\t},');
  lines.push('\t}');
  lines.push('');
  lines.push('\t// 1. Register the scenario');
  lines.push('\tif _, err := client.LoadScenario(scenario); err != nil {');
  lines.push('\t\tpanic(err)');
  lines.push('\t}');
  lines.push('');
  lines.push('\t// 2. Start it (requires loadGenerationEnabled=true)');
  lines.push(`\tif _, err := client.StartLoadScenarios(${goStr(name)}); err != nil {`);
  lines.push('\t\tpanic(err)');
  lines.push('\t}');
  lines.push('\tfmt.Println("load scenario started")');
  lines.push('}');
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// C# — object initializers + LoadStage static factories, then
// client.LoadScenario(scenario) + client.StartLoadScenarios("name").
// NB: the C# HttpRequest has no socket-address surface, so a step's target
// host/port is not emitted (the client API does not support it).
// ---------------------------------------------------------------------------

/** C# `LoadStage.<Factory>(...)` for one stage. */
function csharpStage(stage: LoadStageDTO): string {
  const dur = num(stage.durationMillis);
  const curve = `RampCurve.${stage.curve ?? 'LINEAR'}`;
  switch (stageKind(stage)) {
    case 'vu-hold':
      return `LoadStage.ConstantVus(${num(stage.vus)}, ${dur})`;
    case 'vu-ramp':
      return `LoadStage.RampVus(${num(stage.startVus)}, ${num(stage.endVus)}, ${dur}, ${curve})`;
    case 'rate-hold':
      return `LoadStage.ConstantRate(${num(stage.rate)}, ${dur})`;
    case 'rate-ramp':
      return `LoadStage.RampRate(${num(stage.startRate)}, ${num(stage.endRate)}, ${dur}, ${curve})`;
    case 'pause':
      return `LoadStage.Pause(${dur})`;
  }
}

const csStr = (s: string) => JSON.stringify(s);

/** C# `HttpRequest.Request()....Build()` for a step's request. */
function csharpRequest(request: LoadRequestDTO): string {
  let expr = 'HttpRequest.Request()';
  if (request.method) expr += `.WithMethod(${csStr(request.method)})`;
  if (request.path) expr += `.WithPath(${csStr(request.path)})`;
  if (request.headers) {
    for (const [k, vs] of Object.entries(request.headers)) {
      expr += `.WithHeader(${csStr(k)}, ${vs.map(csStr).join(', ')})`;
    }
  }
  if (request.body != null && request.body !== '') expr += `.WithBody(${csStr(request.body)})`;
  expr += '.Build()';
  return expr;
}

export function loadToCsharp(input: LoadScenarioCodegenInput): string {
  const { scenario } = input;
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(scenario);
  const stages = stagesOf(scenario);
  const steps = scenario.steps ?? [];

  const lines: string[] = [
    'using MockServer.Client;',
    'using MockServer.Client.Models;',
    '',
    `using var client = new MockServerClient("${host}", ${port});`,
    '',
    'var scenario = new LoadScenario',
    '{',
    `    Name = ${csStr(name)},`,
  ];
  if (hasNonDefaultTemplate(scenario)) lines.push(`    TemplateType = LoadTemplateType.${scenario.templateType},`);
  if (scenario.maxRequests != null) lines.push(`    MaxRequests = ${num(scenario.maxRequests)},`);
  if (scenario.startDelayMillis != null) lines.push(`    StartDelayMillis = ${num(scenario.startDelayMillis)},`);
  if (scenario.labels) {
    const entries = Object.entries(scenario.labels);
    lines.push('    Labels = new Dictionary<string, string>');
    lines.push('    {');
    for (const [k, v] of entries) lines.push(`        [${csStr(k)}] = ${csStr(v)},`);
    lines.push('    },');
  }
  // profile
  lines.push('    Profile = new LoadProfile');
  lines.push('    {');
  lines.push('        Stages = new List<LoadStage>');
  lines.push('        {');
  stages.forEach((s) => lines.push(`            ${csharpStage(s)},`));
  lines.push('        },');
  lines.push('    },');
  // steps
  lines.push('    Steps = new List<LoadStep>');
  lines.push('    {');
  steps.forEach((step) => {
    lines.push('        new LoadStep');
    lines.push('        {');
    lines.push(`            Request = ${csharpRequest(step.request ?? {})},`);
    if (step.name) lines.push(`            Name = ${csStr(step.name)},`);
    if (step.thinkTime && num(step.thinkTime.value) > 0) {
      lines.push(`            ThinkTime = new Delay { TimeUnit = TimeUnit.${step.thinkTime.timeUnit ?? 'MILLISECONDS'}, Value = ${num(step.thinkTime.value)} },`);
    }
    lines.push('        },');
  });
  lines.push('    },');
  lines.push('};');
  lines.push('');
  lines.push('// 1. Register the scenario');
  lines.push('client.LoadScenario(scenario);');
  lines.push('');
  lines.push('// 2. Start it (requires loadGenerationEnabled=true)');
  lines.push(`client.StartLoadScenarios(${csStr(name)});`);
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Ruby — MockServer::* models + LoadStage.vu/rate/pause, then
// client.load_scenario(scenario) + client.start_load_scenarios('name').
// ---------------------------------------------------------------------------

/** Ruby `MockServer::LoadStage.<factory>(...)` for one stage. */
function rubyStage(stage: LoadStageDTO): string {
  const dur = num(stage.durationMillis);
  const curve = stage.curve && stage.curve !== 'LINEAR' ? `, curve: '${stage.curve}'` : '';
  switch (stageKind(stage)) {
    case 'vu-hold':
      return `MockServer::LoadStage.vu(${dur}, vus: ${num(stage.vus)})`;
    case 'vu-ramp':
      return `MockServer::LoadStage.vu(${dur}, start_vus: ${num(stage.startVus)}, end_vus: ${num(stage.endVus)}${curve})`;
    case 'rate-hold': {
      const maxVus = stage.maxVus != null ? `, max_vus: ${stage.maxVus}` : '';
      return `MockServer::LoadStage.rate(${dur}, rate: ${num(stage.rate)}${maxVus})`;
    }
    case 'rate-ramp': {
      const maxVus = stage.maxVus != null ? `, max_vus: ${stage.maxVus}` : '';
      return `MockServer::LoadStage.rate(${dur}, start_rate: ${num(stage.startRate)}, end_rate: ${num(stage.endRate)}${maxVus}${curve})`;
    }
    case 'pause':
      return `MockServer::LoadStage.pause(${dur})`;
  }
}

const rubyStr = (s: string) => `'${s.replace(/\\/g, '\\\\').replace(/'/g, "\\'")}'`;

/** Ruby `{ 'Name' => ['v'] }` hash for headers. */
function rubyHeaders(headers: Record<string, string[]>): string {
  const entries = Object.entries(headers);
  if (entries.length === 0) return '{}';
  return '{ ' + entries.map(([k, vs]) => `${rubyStr(k)} => [${vs.map(rubyStr).join(', ')}]`).join(', ') + ' }';
}

/** Ruby `MockServer::HttpRequest.new(...)` for a step's request. */
function rubyRequest(request: LoadRequestDTO): string {
  const args: string[] = [];
  if (request.method) args.push(`method: ${rubyStr(request.method)}`);
  if (request.path) args.push(`path: ${rubyStr(request.path)}`);
  if (request.headers) args.push(`headers: ${rubyHeaders(request.headers)}`);
  if (request.body != null && request.body !== '') args.push(`body: ${rubyStr(request.body)}`);
  const sa = request.socketAddress;
  if (sa && sa.host) {
    args.push(`socket_address: MockServer::SocketAddress.new(host: ${rubyStr(sa.host)}, port: ${num(sa.port)}, scheme: ${rubyStr(sa.scheme ?? 'HTTP')})`);
  }
  return `MockServer::HttpRequest.new(${args.join(', ')})`;
}

function rubyStep(step: LoadStepDTO, indent: string): string {
  const args: string[] = [`request: ${rubyRequest(step.request ?? {})}`];
  if (step.name) args.push(`name: ${rubyStr(step.name)}`);
  if (step.thinkTime && num(step.thinkTime.value) > 0) {
    args.push(`think_time: MockServer::Delay.new(time_unit: ${rubyStr(step.thinkTime.timeUnit ?? 'MILLISECONDS')}, value: ${num(step.thinkTime.value)})`);
  }
  return `MockServer::LoadStep.new(\n${indent}  ${args.join(`,\n${indent}  `)}\n${indent})`;
}

export function loadToRuby(input: LoadScenarioCodegenInput): string {
  const { scenario } = input;
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(scenario);
  const stages = stagesOf(scenario);
  const steps = scenario.steps ?? [];

  const lines: string[] = [
    "require 'mockserver-client'",
    '',
    `client = MockServer::Client.new('${host}', ${port})`,
    '',
    'scenario = MockServer::LoadScenario.new(',
    `  name: ${rubyStr(name)},`,
  ];
  if (hasNonDefaultTemplate(scenario)) lines.push(`  template_type: ${rubyStr(scenario.templateType!)},`);
  if (scenario.maxRequests != null) lines.push(`  max_requests: ${num(scenario.maxRequests)},`);
  if (scenario.startDelayMillis != null) lines.push(`  start_delay_millis: ${num(scenario.startDelayMillis)},`);
  if (scenario.labels) {
    const entries = Object.entries(scenario.labels);
    lines.push(`  labels: { ${entries.map(([k, v]) => `${rubyStr(k)} => ${rubyStr(v)}`).join(', ')} },`);
  }
  // profile
  lines.push('  profile: MockServer::LoadProfile.new(stages: [');
  stages.forEach((s) => lines.push(`    ${rubyStage(s)},`));
  lines.push('  ]),');
  // steps
  lines.push('  steps: [');
  steps.forEach((s) => lines.push(`    ${rubyStep(s, '    ')},`));
  lines.push('  ]');
  lines.push(')');
  lines.push('');
  lines.push('# 1. Register the scenario');
  lines.push('client.load_scenario(scenario)');
  lines.push('');
  lines.push('# 2. Start it (requires loadGenerationEnabled=true)');
  lines.push(`client.start_load_scenarios('${name}')`);
  return lines.join('\n');
}

// ---------------------------------------------------------------------------
// Rust — mockserver_client builders. LoadScenario::new(name, profile, steps)
// then client.load_scenario(&scenario) + client.start_load_scenarios(&["name"]).
// ---------------------------------------------------------------------------

/** Rust `LoadStage::<fn>(...)` for one stage. Rate values are emitted as f64 literals (Rust does
 *  not coerce a bare integer literal to f64), e.g. 50 → 50.0. */
function rustStage(stage: LoadStageDTO): string {
  const dur = num(stage.durationMillis);
  const curve = `RampCurve::${rustCurve(stage.curve)}`;
  switch (stageKind(stage)) {
    case 'vu-hold':
      return `LoadStage::vu_hold(${num(stage.vus)}, ${dur})`;
    case 'vu-ramp':
      return `LoadStage::vu_ramp(${num(stage.startVus)}, ${num(stage.endVus)}, ${dur}, ${curve})`;
    case 'rate-hold': {
      const base = `LoadStage::rate_hold(${rustF64(stage.rate)}, ${dur})`;
      return stage.maxVus != null ? `${base}.max_vus(${stage.maxVus})` : base;
    }
    case 'rate-ramp': {
      const base = `LoadStage::rate_ramp(${rustF64(stage.startRate)}, ${rustF64(stage.endRate)}, ${dur}, ${curve})`;
      return stage.maxVus != null ? `${base}.max_vus(${stage.maxVus})` : base;
    }
    case 'pause':
      return `LoadStage::pause(${dur})`;
  }
}

/** Render a number as a Rust f64 literal — integral values get a trailing `.0` so they type-check. */
function rustF64(v: number | undefined): string {
  const n = num(v);
  return Number.isInteger(n) ? `${n}.0` : String(n);
}

/** RampCurve::Linear / Exponential / Quadratic. */
function rustCurve(curve: string | undefined): string {
  switch (curve) {
    case 'EXPONENTIAL':
      return 'Exponential';
    case 'QUADRATIC':
      return 'Quadratic';
    default:
      return 'Linear';
  }
}

const rustStr = (s: string) => JSON.stringify(s);

/** Rust `HttpRequest::new()....` builder for a step's request. */
function rustRequest(request: LoadRequestDTO): string {
  let expr = 'HttpRequest::new()';
  if (request.method) expr += `\n            .method(${rustStr(request.method)})`;
  if (request.path) expr += `\n            .path(${rustStr(request.path)})`;
  if (request.headers) {
    for (const [k, vs] of Object.entries(request.headers)) {
      for (const v of vs) expr += `\n            .header(${rustStr(k)}, ${rustStr(v)})`;
    }
  }
  if (request.body != null && request.body !== '') expr += `\n            .body(${rustStr(request.body)})`;
  const sa = request.socketAddress;
  if (sa && sa.host) {
    expr += `\n            .socket_address(SocketAddress::new(${rustStr(sa.host)}, ${num(sa.port)}).scheme(${rustStr(sa.scheme ?? 'HTTP')}))`;
  }
  return expr;
}

function rustStep(step: LoadStepDTO): string {
  let expr = `LoadStep::new(\n        ${rustRequest(step.request ?? {})}\n    )`;
  if (step.thinkTime && num(step.thinkTime.value) > 0) {
    expr += `.think_time(Delay::milliseconds(${num(step.thinkTime.value)}))`;
  }
  return expr;
}

export function loadToRust(input: LoadScenarioCodegenInput): string {
  const { scenario } = input;
  const { host, port } = clientHostPort(input.baseUrl);
  const name = nameOr(scenario);
  const stages = stagesOf(scenario);
  const steps = scenario.steps ?? [];

  const lines: string[] = [
    `// Cargo.toml: mockserver-client = "${rustClientVersionRequirement()}"`,
    'use mockserver_client::{',
    '    ClientBuilder, LoadScenario, LoadProfile, LoadStage, LoadStep,',
    '    RampCurve, HttpRequest, SocketAddress, Delay,',
    '};',
    '',
    'fn main() -> mockserver_client::Result<()> {',
    `    let client = ClientBuilder::new("${host}", ${port}).build()?;`,
    '',
    '    let profile = LoadProfile::of(vec![',
  ];
  stages.forEach((s) => lines.push(`        ${rustStage(s)},`));
  lines.push('    ]);');
  lines.push('');
  lines.push('    let steps = vec![');
  steps.forEach((step) => {
    const stepExpr = rustStep(step);
    const indented = stepExpr.split('\n').map((l, i) => (i === 0 ? `        ${l}` : l)).join('\n');
    lines.push(`${indented},`);
  });
  lines.push('    ];');
  lines.push('');
  const scenarioExpr = `    let scenario = LoadScenario::new(${rustStr(name)}, profile, steps)`;
  const chain: string[] = [];
  if (hasNonDefaultTemplate(scenario)) chain.push(`.template_type(${rustStr(scenario.templateType!)})`);
  if (scenario.maxRequests != null) chain.push(`.max_requests(${num(scenario.maxRequests)})`);
  if (scenario.startDelayMillis != null) chain.push(`.start_delay_millis(${num(scenario.startDelayMillis)})`);
  if (chain.length > 0) {
    lines.push(scenarioExpr);
    chain.forEach((c, i) => lines.push(`        ${c}${i === chain.length - 1 ? ';' : ''}`));
  } else {
    lines.push(`${scenarioExpr};`);
  }
  lines.push('');
  lines.push('    // 1. Register the scenario');
  lines.push('    client.load_scenario(&scenario)?;');
  lines.push('');
  lines.push('    // 2. Start it (requires loadGenerationEnabled=true)');
  lines.push(`    client.start_load_scenarios(&["${name}"])?;`);
  lines.push('    Ok(())');
  lines.push('}');
  return lines.join('\n');
}
