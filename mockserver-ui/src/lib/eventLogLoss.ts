/**
 * What MockServer's event log had lost when a verification could not be trusted.
 *
 * An upper-bound verification (`never`, `atMost`, `exactly`, `between`) fails
 * rather than passes once the event log is no longer a complete record. The
 * `VERIFICATION_FAILED` log entry then carries, as a message argument, the same
 * counts and bounds the REST client gets in the failure message. Only the causes
 * that happened are present, and every count is since the event log was last
 * reset.
 *
 * The field names are the server's (`MockServerEventLog`); the shared fixture
 * `__fixtures__/incompleteLogVerificationFailure.json` is checked on both sides.
 */
const COUNT_FIELDS = [
  'droppedRingFull',
  'droppedInFlightBytes',
  'evictedAtMaxLogEntries',
  'evictedAtMaxEventLogSizeInBytes',
] as const;

const BOUND_FIELDS = ['inFlightBytesBudget', 'maxLogEntries', 'maxEventLogSizeInBytes'] as const;

type LossField = (typeof COUNT_FIELDS)[number] | (typeof BOUND_FIELDS)[number];

export type EventLogLoss = Partial<Record<LossField, number>>;

export type EventLogLossCauseId = 'ring-full' | 'in-flight-bytes' | 'max-log-entries' | 'max-event-log-size-in-bytes';

export interface EventLogLossCause {
  id: EventLogLossCauseId;
  /** What was lost, how much, and which bound was reached. */
  lost: string;
  /** The setting to change. */
  fix: string;
}

export const EVENT_LOG_LOSS_LEAD =
  'Absence cannot be proven: the matching requests may have been discarded rather than never made. '
  + 'Since the event log was last reset:';

export const EVENT_LOG_LOSS_ALTERNATIVES =
  'Or reset the event log between tests, or set failVerificationOnEvictedLog=false to restore the '
  + 'previous (unsound) behaviour.';

const KNOWN_FIELDS: ReadonlySet<string> = new Set<string>([...COUNT_FIELDS, ...BOUND_FIELDS]);

/**
 * Read a message-part value as an event-log loss summary, or `null` when it is
 * anything else. Strict on purpose: an object with a field this dashboard does
 * not know, or a value that is not a count, is left to the generic JSON view
 * rather than shown with part of it missing.
 */
export function parseEventLogLoss(value: unknown): EventLogLoss | null {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return null;
  const entries = Object.entries(value);
  if (entries.length === 0) return null;
  const loss: EventLogLoss = {};
  for (const [field, fieldValue] of entries) {
    if (!KNOWN_FIELDS.has(field)) return null;
    if (typeof fieldValue !== 'number' || !Number.isFinite(fieldValue) || fieldValue < 0) return null;
    loss[field as LossField] = fieldValue;
  }
  return COUNT_FIELDS.some((field) => (loss[field] ?? 0) > 0) ? loss : null;
}

const MATCH_FAILURE_CAP = 'maxLoggedMatchFailures (e.g. to 10) to cap the did-not-match entries logged per request';

function counted(count: number, one: string, many: string): string {
  return `${count.toLocaleString()} ${count === 1 ? one : many}`;
}

/** One entry per cause that happened, in the order the server's failure message lists them. */
export function eventLogLossCauses(loss: EventLogLoss): EventLogLossCause[] {
  const causes: EventLogLossCause[] = [];
  const ringFull = loss.droppedRingFull ?? 0;
  if (ringFull > 0) {
    causes.push({
      id: 'ring-full',
      lost: `${counted(ringFull, 'log event was', 'log events were')} dropped before being recorded because the `
        + 'ring buffer was full (log events arrived faster than the single logging thread could record them).',
      fix: 'If the drops persist under steady load, lower the log level (e.g. to WARN), or keep the log level and '
        + `set ${MATCH_FAILURE_CAP}; a larger ringBufferSize only absorbs short bursts.`,
    });
  }
  const inFlightBytes = loss.droppedInFlightBytes ?? 0;
  if (inFlightBytes > 0) {
    const budget = loss.inFlightBytesBudget;
    causes.push({
      id: 'in-flight-bytes',
      lost: `${counted(inFlightBytes, 'log event was', 'log events were')} dropped before being recorded because `
        + 'the request and response bodies waiting to be logged exceeded the in-flight byte budget'
        + `${budget === undefined ? '' : ` of ${budget.toLocaleString()} bytes`} (the larger of `
        + 'maxEventLogSizeInBytes and a heap-derived cap).',
      fix: 'Lower the log level or, if you have heap to spare, raise maxEventLogSizeInBytes above that budget. '
        + 'maxLoggedBodyBytes does not help: it truncates bodies only after they leave this backlog.',
    });
  }
  const countEvicted = loss.evictedAtMaxLogEntries ?? 0;
  if (countEvicted > 0) {
    causes.push({
      id: 'max-log-entries',
      lost: `${counted(countEvicted, 'recorded entry was', 'recorded entries were')} evicted after the log reached `
        + `its maximum number of entries (${setting('maxLogEntries', loss.maxLogEntries)}).`,
      fix: 'Raise maxLogEntries, or lower the log level so fewer entries are recorded per request, or keep the log '
        + `level and set ${MATCH_FAILURE_CAP}.`,
    });
  }
  const byteEvicted = loss.evictedAtMaxEventLogSizeInBytes ?? 0;
  if (byteEvicted > 0) {
    causes.push({
      id: 'max-event-log-size-in-bytes',
      lost: `${counted(byteEvicted, 'recorded entry was', 'recorded entries were')} evicted after the log reached `
        + `its maximum size in bytes (${setting('maxEventLogSizeInBytes', loss.maxEventLogSizeInBytes)}).`,
      fix: 'Raise maxEventLogSizeInBytes, or set maxLoggedBodyBytes to truncate large bodies so each recorded '
        + 'entry is smaller.',
    });
  }
  return causes;
}

// The bound as the property assignment it is, so it can be copied: no digit grouping.
function setting(name: string, value: number | undefined): string {
  return value === undefined ? name : `${name}=${value}`;
}

/** The summary as plain text, for copying a log entry. */
export function eventLogLossText(loss: EventLogLoss): string {
  return [
    EVENT_LOG_LOSS_LEAD,
    ...eventLogLossCauses(loss).map((cause) => `- ${cause.lost} ${cause.fix}`),
    EVENT_LOG_LOSS_ALTERNATIVES,
  ].join('\n');
}
