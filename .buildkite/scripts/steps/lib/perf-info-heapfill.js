// Event-log fill load for perf-test-info-investigation.sh (docs/infrastructure/ci-cd.md). Eight closed-loop
// clients send unique ~10 KB bodies until the harness stops the container.
//   MODE=json   POST $TARGET with a unique JSON body (the SUT answers a static {"ok":true} mock)
//   MODE=proxy  POST $TARGET (an absolute http:// URL on the upstream) with a unique text/plain body;
//               k6 sends it through HTTP_PROXY, so the SUT forwards it as a proxy
import http from 'k6/http';

const MODE = __ENV.MODE || 'json';
const TARGET = __ENV.TARGET;
const BODY_BYTES = Number(__ENV.BODY_BYTES || 10240);
const PAD = 'x'.repeat(BODY_BYTES);
const ITERATIONS = Number(__ENV.ITERATIONS || 0);

export const options = {
  discardResponseBodies: true,
  scenarios: {
    fill: ITERATIONS > 0
      ? { executor: 'shared-iterations', vus: 8, iterations: ITERATIONS, maxDuration: '5m' }
      : { executor: 'constant-vus', vus: 8, duration: __ENV.DURATION || '30m' },
  },
};

export default function () {
  const id = `${__VU}-${__ITER}-${Date.now()}`;
  if (MODE === 'json') {
    const body = `{"id":"${id}","pad":"${PAD.substring(0, BODY_BYTES - id.length - 18)}"}`;
    http.post(TARGET, body, { headers: { 'Content-Type': 'application/json' } });
  } else {
    http.post(TARGET, `${id} ${PAD.substring(0, BODY_BYTES - id.length - 1)}`, { headers: { 'Content-Type': 'text/plain' } });
  }
}
