import { describe, it, expect } from 'vitest';
import { isLogGroup } from '../types';
import type { LogEntry, LogGroup } from '../types';

describe('isLogGroup', () => {
  it('returns true for a log group', () => {
    const group: LogGroup = {
      key: 'g1',
      group: { key: 'g1_summary', value: { messageParts: [] } },
      value: [{ key: 'child1', value: { messageParts: [] } }],
    };
    expect(isLogGroup(group)).toBe(true);
  });

  it('returns false for a regular log entry', () => {
    const entry: LogEntry = {
      key: 'e1',
      value: { messageParts: [] },
    };
    expect(isLogGroup(entry)).toBe(false);
  });
});
