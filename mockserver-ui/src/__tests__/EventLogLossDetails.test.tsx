import { describe, it, expect } from 'vitest';
import { render, screen, within } from '@testing-library/react';
import LogEntry from '../components/LogEntry';
import { entryToText } from '../lib/logEntryText';
import { eventLogLossCauses, parseEventLogLoss } from '../lib/eventLogLoss';
import type { LogEntryValue, MessagePart } from '../types';
import fixture from '../__fixtures__/incompleteLogVerificationFailure.json';

/**
 * What a user reading the dashboard log sees on a failed verification: for one
 * that failed because the event log was incomplete, which bound was reached, how
 * many entries were lost and the setting to change; for an ordinary failure,
 * what it showed before.
 */

const ALL_CAUSES = fixture.logEntry as LogEntryValue;

/** The entry the server logs when only the given causes happened. */
function incompleteLogEntry(reason: string, loss: Record<string, unknown>): LogEntryValue {
  return {
    description: 'VERIFICATION_FAILED',
    messageParts: [
      { key: 'id_0msg', value: 'request:' },
      { key: 'id_0arg', json: true, argument: true, value: { path: '/absent' } },
      { key: 'id_1msg', value: `could not be verified exactly 0 times because the event log has ${reason}:` },
      { key: 'id_1arg', json: true, argument: true, value: loss } as MessagePart,
    ],
  };
}

function shownCauses(): string[] {
  return within(screen.getByTestId('event-log-loss'))
    .getAllByRole('listitem')
    .map((item) => item.getAttribute('data-testid') ?? '');
}

describe('failed verification on an incomplete event log', () => {
  it('names the count bound, how many entries it evicted and the setting to raise', () => {
    render(<LogEntry entry={incompleteLogEntry('evicted entries', { evictedAtMaxLogEntries: 1234, maxLogEntries: 1000 })} />);

    expect(shownCauses()).toEqual(['event-log-loss-max-log-entries']);
    const cause = screen.getByTestId('event-log-loss-max-log-entries');
    expect(cause).toHaveTextContent(
      '1,234 recorded entries were evicted after the log reached its maximum number of entries (maxLogEntries=1000).',
    );
    expect(cause).toHaveTextContent(
      'Raise maxLogEntries, or lower the log level so fewer entries are recorded per request, or keep the log level '
      + 'and set maxLoggedMatchFailures (e.g. to 10) to cap the did-not-match entries logged per request.',
    );
    expect(screen.getByTestId('event-log-loss')).not.toHaveTextContent('maxEventLogSizeInBytes');
  });

  it('names the byte bound, how many entries it evicted and the setting to raise', () => {
    render(<LogEntry entry={incompleteLogEntry('evicted entries', { evictedAtMaxEventLogSizeInBytes: 7, maxEventLogSizeInBytes: 47290368 })} />);

    expect(shownCauses()).toEqual(['event-log-loss-max-event-log-size-in-bytes']);
    const cause = screen.getByTestId('event-log-loss-max-event-log-size-in-bytes');
    expect(cause).toHaveTextContent(
      '7 recorded entries were evicted after the log reached its maximum size in bytes (maxEventLogSizeInBytes=47290368).',
    );
    expect(cause).toHaveTextContent(
      'Raise maxEventLogSizeInBytes, or set maxLoggedBodyBytes to truncate large bodies so each recorded entry is smaller.',
    );
    expect(screen.getByTestId('event-log-loss')).not.toHaveTextContent('maxLogEntries');
    expect(screen.getByTestId('event-log-loss')).not.toHaveTextContent('maxLoggedMatchFailures');
  });

  it('says how many events were dropped at a full ring and to lower the log level', () => {
    render(<LogEntry entry={incompleteLogEntry('dropped log events', { droppedRingFull: 27 })} />);

    expect(shownCauses()).toEqual(['event-log-loss-ring-full']);
    const cause = screen.getByTestId('event-log-loss-ring-full');
    expect(cause).toHaveTextContent('27 log events were dropped before being recorded because the ring buffer was full');
    expect(cause).toHaveTextContent(
      'If the drops persist under steady load, lower the log level (e.g. to WARN), or keep the log level and set '
      + 'maxLoggedMatchFailures (e.g. to 10) to cap the did-not-match entries logged per request; a larger '
      + 'ringBufferSize only absorbs short bursts.',
    );
    expect(screen.getByTestId('event-log-loss')).not.toHaveTextContent('evicted');
  });

  it('says how many events were dropped at the in-flight byte budget, the budget and the setting to raise', () => {
    render(<LogEntry entry={incompleteLogEntry('dropped log events', { droppedInFlightBytes: 3, inFlightBytesBudget: 47290368 })} />);

    expect(shownCauses()).toEqual(['event-log-loss-in-flight-bytes']);
    const cause = screen.getByTestId('event-log-loss-in-flight-bytes');
    expect(cause).toHaveTextContent(
      '3 log events were dropped before being recorded because the request and response bodies waiting to be logged '
      + 'exceeded the in-flight byte budget of 47,290,368 bytes',
    );
    expect(cause).toHaveTextContent('raise maxEventLogSizeInBytes above that budget');
    expect(cause).toHaveTextContent('maxLoggedBodyBytes does not help');
    expect(screen.getByTestId('event-log-loss')).not.toHaveTextContent('ring buffer');
    expect(screen.getByTestId('event-log-loss')).not.toHaveTextContent('maxLoggedMatchFailures');
  });

  it('uses the singular for a single lost entry', () => {
    render(<LogEntry entry={incompleteLogEntry('dropped log events and evicted entries', { droppedRingFull: 1, evictedAtMaxLogEntries: 1, maxLogEntries: 8 })} />);

    expect(screen.getByTestId('event-log-loss-ring-full')).toHaveTextContent('1 log event was dropped');
    expect(screen.getByTestId('event-log-loss-max-log-entries')).toHaveTextContent('1 recorded entry was evicted');
  });

  it('shows every cause of the entry the server sends, in the order of its failure message', () => {
    render(<LogEntry entry={ALL_CAUSES} />);

    expect(screen.getByText('request:')).toBeInTheDocument();
    expect(screen.getByText(/could not be verified exactly 0 times because the event log has dropped log events and evicted entries:/)).toBeInTheDocument();
    expect(shownCauses()).toEqual([
      'event-log-loss-ring-full',
      'event-log-loss-in-flight-bytes',
      'event-log-loss-max-log-entries',
      'event-log-loss-max-event-log-size-in-bytes',
    ]);
    expect(screen.getByTestId('event-log-loss-ring-full')).toHaveTextContent('16 log events were dropped');
    expect(screen.getByTestId('event-log-loss-in-flight-bytes')).toHaveTextContent('3 log events were dropped');
    expect(screen.getByTestId('event-log-loss-in-flight-bytes')).toHaveTextContent('budget of 1,000 bytes');
    expect(screen.getByTestId('event-log-loss-max-log-entries')).toHaveTextContent('3 recorded entries were evicted');
    expect(screen.getByTestId('event-log-loss-max-log-entries')).toHaveTextContent('(maxLogEntries=3)');
    expect(screen.getByTestId('event-log-loss-max-event-log-size-in-bytes')).toHaveTextContent('2 recorded entries were evicted');
    expect(screen.getByTestId('event-log-loss-max-event-log-size-in-bytes')).toHaveTextContent('(maxEventLogSizeInBytes=4000)');
  });

  it('says why absence cannot be proven, that the counts are since the last reset, and the alternatives', () => {
    render(<LogEntry entry={ALL_CAUSES} />);

    const details = screen.getByRole('note', { name: 'What the event log lost and how to fix it' });
    expect(details).toHaveTextContent(
      'Absence cannot be proven: the matching requests may have been discarded rather than never made.',
    );
    expect(details).toHaveTextContent('Since the event log was last reset:');
    expect(details).toHaveTextContent(
      'Or reset the event log between tests, or set failVerificationOnEvictedLog=false to restore the previous (unsound) behaviour.',
    );
  });

  it('shows the summary as sentences, not as its raw counters', () => {
    render(<LogEntry entry={ALL_CAUSES} />);

    expect(document.body).not.toHaveTextContent('droppedRingFull');
    expect(document.body).not.toHaveTextContent('evictedAtMaxLogEntries');
  });

  it('understands every field of the summary the server sends', () => {
    const lossPart = ALL_CAUSES.messageParts![3]!;
    const loss = parseEventLogLoss(lossPart.value);

    expect(loss).toEqual(lossPart.value);
    expect(eventLogLossCauses(loss!).map((cause) => cause.id)).toEqual([
      'ring-full',
      'in-flight-bytes',
      'max-log-entries',
      'max-event-log-size-in-bytes',
    ]);
  });

  it('still offers "Create from this request" for the verified request, not for the loss summary', () => {
    render(<LogEntry entry={ALL_CAUSES} collapsible />);

    expect(screen.getByRole('button', { name: 'Create from this request…' })).toBeInTheDocument();
  });

  it('copies the sentences the row shows', () => {
    const text = entryToText(incompleteLogEntry('evicted entries', { evictedAtMaxLogEntries: 2, maxLogEntries: 8 }));

    expect(text).toContain('because the event log has evicted entries:');
    expect(text).toContain('- 2 recorded entries were evicted after the log reached its maximum number of entries (maxLogEntries=8). Raise maxLogEntries');
    expect(text).toContain('set failVerificationOnEvictedLog=false');
    expect(text).not.toContain('evictedAtMaxLogEntries');
  });
});

describe('a summary this dashboard cannot read in full', () => {
  it.each([
    ['a field it does not know', { evictedAtMaxLogEntries: 2, maxLogEntries: 8, droppedSomewhereNew: 4 }],
    ['a count that is not a number', { evictedAtMaxLogEntries: '2', maxLogEntries: 8 }],
    ['a bound that is not a number', { evictedAtMaxLogEntries: 2, maxLogEntries: '***' }],
    ['a negative count', { evictedAtMaxLogEntries: 2, maxLogEntries: 8, droppedRingFull: -1 }],
    ['no loss at all', { evictedAtMaxLogEntries: 0, maxLogEntries: 8 }],
  ])('is shown as plain JSON when it has %s', (_name, loss) => {
    const entry = incompleteLogEntry('evicted entries', loss);
    render(<LogEntry entry={entry} />);

    expect(screen.queryByTestId('event-log-loss')).not.toBeInTheDocument();
    expect(entryToText(entry)).toContain('"evictedAtMaxLogEntries"');
  });

  it.each([null, 'text', 3, [], {}, ['droppedRingFull']])('does not read %j as a summary', (value) => {
    expect(parseEventLogLoss(value)).toBeNull();
  });
});

describe('an ordinary failed verification', () => {
  const notFound: LogEntryValue = {
    description: 'VERIFICATION_FAILED',
    messageParts: [
      { key: 'id_0msg', value: 'request not found exactly 2 times, expected:' },
      { key: 'id_0arg', json: true, argument: true, value: { path: '/expected' } },
      { key: 'id_1msg', value: 'but was:' },
      { key: 'id_1arg', json: true, argument: true, value: { method: 'GET', path: '/actual' } },
    ],
  };

  it('shows the expected and actual requests and no event-log summary', () => {
    render(<LogEntry entry={notFound} />);

    expect(screen.getByText('request not found exactly 2 times, expected:')).toBeInTheDocument();
    expect(screen.getByText('but was:')).toBeInTheDocument();
    expect(screen.queryByTestId('event-log-loss')).not.toBeInTheDocument();
    expect(screen.queryByRole('note')).not.toBeInTheDocument();
  });

  it('copies both requests as JSON', () => {
    const text = entryToText(notFound);

    expect(text).toContain('"path": "/expected"');
    expect(text).toContain('"path": "/actual"');
  });
});
