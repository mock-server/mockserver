import { describe, it, expect } from 'vitest';
import { buildTheme, logTypeColors, becauseColors } from '../theme';

describe('buildTheme', () => {
  it('creates a dark theme with correct palette mode', () => {
    const theme = buildTheme('dark');
    expect(theme.palette.mode).toBe('dark');
  });

  it('creates a light theme with correct palette mode', () => {
    const theme = buildTheme('light');
    expect(theme.palette.mode).toBe('light');
  });

  it('dark theme has dark background', () => {
    const theme = buildTheme('dark');
    expect(theme.palette.background.default).toBe('#121212');
  });

  it('light theme has light background', () => {
    const theme = buildTheme('light');
    expect(theme.palette.background.default).toBe('#fafafa');
  });
});

describe('logTypeColors', () => {
  it('has all 18+ log type colours defined', () => {
    const knownTypes = [
      'TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'EXCEPTION',
      'CLEARED', 'RETRIEVED', 'UPDATED_EXPECTATION', 'CREATED_EXPECTATION',
      'REMOVED_EXPECTATION', 'RECEIVED_REQUEST', 'EXPECTATION_RESPONSE',
      'NO_MATCH_RESPONSE', 'EXPECTATION_MATCHED', 'EXPECTATION_NOT_MATCHED',
      'VERIFICATION', 'VERIFICATION_FAILED', 'FORWARDED_REQUEST',
      'TEMPLATE_GENERATED', 'SERVER_CONFIGURATION', 'DEFAULT',
    ] as const;

    for (const type of knownTypes) {
      expect(logTypeColors[type]).toBeDefined();
      expect(logTypeColors[type]).toMatch(/^rgb\(/);
    }
  });
});

describe('becauseColors', () => {
  it('has matched, didntMatch, and neutral colours', () => {
    expect(becauseColors.matched).toBeDefined();
    expect(becauseColors.didntMatch).toBeDefined();
    expect(becauseColors.neutral).toBeDefined();
  });

  it('matched is green-ish, didntMatch is red-ish', () => {
    expect(becauseColors.matched).toContain('107, 199, 118');
    expect(becauseColors.didntMatch).toContain('216, 88, 118');
  });
});
