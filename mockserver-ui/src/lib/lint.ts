/**
 * Client for MockServer's expectation lint:
 *   PUT /mockserver/lint  (empty body: lint the active expectations in serving order)
 *
 * Lint is advisory: it finds expectations that can never match, or will not match
 * the way they read, and never changes anything. `PUT /mockserver/expectation`
 * runs the per-expectation rules too and counts what it found in the
 * `x-mockserver-lint-warnings` response header.
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';

export type LintSeverity = 'ERROR' | 'WARNING' | 'INFO';

export interface LintIssue {
  rule: string;
  severity: LintSeverity;
  expectationId?: string;
  /** 1-based position in serving order. */
  position?: number;
  summary?: string;
  field?: string;
  message: string;
  /** For SHADOWED: the expectation evaluated earlier that always matches first. */
  relatedExpectationId?: string;
  relatedPosition?: number;
  relatedSummary?: string;
}

export interface LintReport {
  note?: string;
  expectationsChecked: number;
  inactiveExpectationsSkipped: number;
  issueCount: number;
  errorCount: number;
  warningCount: number;
  infoCount: number;
  issuesTruncated: boolean;
  shadowCheckTruncated: boolean;
  issues: LintIssue[];
}

export const LINT_WARNINGS_HEADER = 'x-mockserver-lint-warnings';

/** Lint the active expectations. Throws in the `MockServer returned <status>: <body>` shape on a non-2xx response. */
export async function lintActiveExpectations(params: ConnectionParams, signal?: AbortSignal): Promise<LintReport> {
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/lint`, { method: 'PUT', signal });
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`MockServer returned ${res.status}: ${body}`);
  }
  const report = (await res.json()) as Partial<LintReport>;
  const num = (value: unknown) => (typeof value === 'number' ? value : 0);
  const issues = Array.isArray(report.issues) ? report.issues : [];
  return {
    note: report.note,
    expectationsChecked: num(report.expectationsChecked),
    inactiveExpectationsSkipped: num(report.inactiveExpectationsSkipped),
    issueCount: num(report.issueCount ?? issues.length),
    errorCount: num(report.errorCount),
    warningCount: num(report.warningCount),
    infoCount: num(report.infoCount),
    issuesTruncated: report.issuesTruncated === true,
    shadowCheckTruncated: report.shadowCheckTruncated === true,
    issues,
  };
}

/**
 * The lint warning count `PUT /mockserver/expectation` reported, or 0 when the header is absent
 * (no warnings, an older server, or a cross-origin page the header is not exposed to).
 */
export function lintWarningCount(res: Response): number {
  const raw = res.headers?.get(LINT_WARNINGS_HEADER) ?? null;
  const count = raw === null ? NaN : Number.parseInt(raw, 10);
  return Number.isFinite(count) && count > 0 ? count : 0;
}
