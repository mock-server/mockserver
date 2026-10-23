// Boot a REAL MockServer (the runnable netty "no-dependencies" JAR) for the
// Playwright end-to-end suite. The JAR serves the freshly-built dashboard from
// `/mockserver/dashboard/` on the SAME origin as its control plane and the
// `/_mockserver_ui_websocket` live feed, so the e2e tests drive the actual
// browser against a real server over real REST + WebSocket — no mocked fetch,
// no jsdom.
//
// This is invoked by playwright.config.ts as a `webServer.command`, once per
// server role. Playwright waits for that entry's `url` (a GET of the dashboard)
// to answer before running the tests, and sends SIGTERM to this process (and
// thus the JVM) on teardown.
//
// Roles (first argument):
//   main       the server under test (E2E_MS_PORT, default 1084). Logs at INFO
//              (the log-panel assertions read received-request entries), with
//              load generation and SLO tracking on; metrics stay OFF, because a
//              test proves the dashboard never polls them on such a server.
//   secondary  a second server (E2E_UPSTREAM_PORT, default 1114): the proxied
//              "upstream" for the library / verify tests, a server without SLO
//              tracking, and the server with metrics on for the Metrics view.
//
// The JAR is located newest-first under mockserver-netty-no-dependencies/target.
// If none exists the main role builds it with Maven (the `build-ui` profile
// bundles the current UI source into the JAR); Playwright starts the web servers
// in order, so the secondary role finds the jar the main role built. In CI the
// JAR is built by the pipeline step BEFORE Playwright runs.
//
// Each JVM's output goes to test-reports/mockserver-<role>.log (a CI artifact),
// not to the console: at INFO the main server logs every request.
//
// Env:
//   E2E_MS_PORT        main server port (default 1084 — deliberately not 1080, so
//                      the suite never silently reuses a hand-started demo server
//                      running a stale dashboard build).
//   E2E_UPSTREAM_PORT  secondary server port (default 1114).
//   E2E_MS_JAR         explicit path to a runnable JAR (skips discovery/build).
//   E2E_JAVA           java executable (default `java` on the PATH).

import { spawn, spawnSync } from 'node:child_process';
import { readdirSync, statSync, existsSync, mkdirSync, mkdtempSync, openSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const __dirname = dirname(fileURLToPath(import.meta.url));
const uiDir = resolve(__dirname, '..');
const repoRoot = resolve(uiDir, '..');
const targetDir = join(repoRoot, 'mockserver', 'mockserver-netty-no-dependencies', 'target');

const ROLES = {
  main: {
    port: process.env.E2E_MS_PORT || '1084',
    jvmArgs: [
      '-Xmx1g',
      '-Dmockserver.maxLogEntries=20000',
      '-Dmockserver.loadGenerationEnabled=true',
      '-Dmockserver.sloTrackingEnabled=true',
      // fixed at startup, so the audit e2e tests cannot switch it on over the control plane
      '-Dmockserver.controlPlaneAuditEnabled=true',
    ],
    logLevel: 'INFO',
  },
  secondary: {
    port: process.env.E2E_UPSTREAM_PORT || '1114',
    jvmArgs: ['-Xmx512m', '-Dmockserver.maxLogEntries=5000', '-Dmockserver.metricsEnabled=true'],
    logLevel: 'INFO',
  },
};

const roleName = process.argv[2] || 'main';
const role = ROLES[roleName];
if (!role) {
  console.error(`[e2e] unknown server role '${roleName}' (expected one of: ${Object.keys(ROLES).join(', ')})`);
  process.exit(2);
}

function findJar() {
  if (process.env.E2E_MS_JAR) {
    return existsSync(process.env.E2E_MS_JAR) ? process.env.E2E_MS_JAR : null;
  }
  if (!existsSync(targetDir)) return null;
  const candidates = readdirSync(targetDir)
    .filter(
      (f) =>
        f.startsWith('mockserver-netty-no-dependencies-') &&
        f.endsWith('.jar') &&
        !f.includes('-sources') &&
        !f.includes('-javadoc') &&
        !f.startsWith('original-'),
    )
    .map((f) => join(targetDir, f))
    .map((p) => ({ p, mtime: statSync(p).mtimeMs }))
    .sort((a, b) => b.mtime - a.mtime);
  return candidates.length > 0 ? candidates[0].p : null;
}

function buildJar() {
  console.error(
    '[e2e] No runnable MockServer JAR found — building it (mvnw install -pl mockserver-netty-no-dependencies -am -DskipTests). This can take a few minutes…',
  );
  const mvnw = join(repoRoot, 'mockserver', 'mvnw');
  const result = spawnSync(
    mvnw,
    ['install', '-DskipTests', '-pl', 'mockserver-netty-no-dependencies', '-am', '-q'],
    { cwd: join(repoRoot, 'mockserver'), stdio: ['ignore', 'inherit', 'inherit'] },
  );
  if (result.status !== 0) {
    console.error('[e2e] MockServer JAR build FAILED');
    process.exit(1);
  }
}

let jar = findJar();
if (!jar && roleName === 'main') {
  buildJar();
  jar = findJar();
}
if (!jar) {
  console.error('[e2e] Could not locate a runnable MockServer JAR');
  process.exit(1);
}

const logDir = join(uiDir, 'test-reports');
mkdirSync(logDir, { recursive: true });
const logFile = join(logDir, `mockserver-${roleName}.log`);
const out = openSync(logFile, 'w');
console.error(`[e2e] Booting the ${roleName} MockServer on port ${role.port} from ${jar} (log: ${logFile})`);

// Run in a throwaway temp dir so MockServer's startup artifacts (e.g. the
// exported mockserver-ca.pem) and the cassette files the library tests write
// (relative paths under .tmp/) never land in the source tree.
const cwd = mkdtempSync(join(tmpdir(), `mockserver-e2e-${roleName}-`));
mkdirSync(join(cwd, '.tmp'));
const child = spawn(
  process.env.E2E_JAVA || 'java',
  [...role.jvmArgs, '-jar', jar, '-serverPort', role.port, '-logLevel', role.logLevel],
  { stdio: ['ignore', out, out], cwd },
);

// Forward termination from Playwright (SIGTERM/SIGINT) to the JVM so no server
// is left listening after the run.
for (const signal of ['SIGTERM', 'SIGINT']) {
  process.on(signal, () => {
    child.kill('SIGTERM');
    process.exit(0);
  });
}
child.on('exit', (code) => {
  console.error(`[e2e] the ${roleName} MockServer exited with code ${code} (log: ${logFile})`);
  process.exit(code ?? 0);
});
