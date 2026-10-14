/**
 * Reads the event-log loss counters MockServer exposes on its Prometheus
 * metrics endpoint (`GET /mockserver/metrics`), split by cause so the
 * dashboard can give the remedy that fits:
 *
 * - `mock_server_dropped_log_events_total{reason="ring_full"}` — events dropped
 *   because they arrived faster than the single logging thread could record
 *   them (lower the log level or set `maxLoggedMatchFailures`; a bigger
 *   `ringBufferSize` only absorbs bursts).
 * - `mock_server_dropped_log_events_total{reason="in_flight_bytes"}` — events
 *   dropped because the bodies waiting to be logged exceeded the in-flight
 *   byte cap.
 * - `mock_server_evicted_log_entries_total` — recorded entries evicted, oldest
 *   first, by the retention bound (`maxLogEntries` / `maxEventLogSizeInBytes`).
 *
 * A server that predates the `reason` label exposes one unlabelled drop series;
 * its value, and any reason this dashboard does not know, is reported as
 * `unattributed` so no drop goes uncounted. Every counter is only present when
 * the server was started with metrics enabled; absent counters read as 0.
 */
import { parsePrometheusText, metricValue, type PrometheusSample } from './prometheusParser';

// The server's Prometheus client appends `_total` to every counter, so the wire
// names carry it even though the counters are registered without it.
export const DROPPED_LOG_EVENTS_METRIC = 'mock_server_dropped_log_events_total';
export const EVICTED_LOG_ENTRIES_METRIC = 'mock_server_evicted_log_entries_total';

export interface LogPressure {
  /** Dropped because the event-log ring buffer was full. */
  ringFull: number;
  /** Dropped because the bodies waiting to be logged exceeded the in-flight byte cap. */
  inFlightBytes: number;
  /** Dropped, with no `reason` label (older server) or a reason this dashboard does not know. */
  unattributed: number;
  /** Recorded entries evicted by the retention bound. */
  evicted: number;
}

/** Every event dropped before being recorded, whatever the cause. */
export function totalDropped(pressure: LogPressure): number {
  return pressure.ringFull + pressure.inFlightBytes + pressure.unattributed;
}

function droppedFor(samples: PrometheusSample[], predicate: (reason: string | undefined) => boolean): number {
  return samples.reduce(
    (total, s) => (s.name === DROPPED_LOG_EVENTS_METRIC && predicate(s.labels.reason) ? total + s.value : total),
    0,
  );
}

/** Extract the event-log loss counters from a Prometheus exposition document. */
export function parseLogPressure(prometheusText: string): LogPressure {
  const samples = parsePrometheusText(prometheusText);
  return {
    ringFull: droppedFor(samples, (reason) => reason === 'ring_full'),
    inFlightBytes: droppedFor(samples, (reason) => reason === 'in_flight_bytes'),
    unattributed: droppedFor(samples, (reason) => reason !== 'ring_full' && reason !== 'in_flight_bytes'),
    evicted: metricValue(samples, EVICTED_LOG_ENTRIES_METRIC),
  };
}
