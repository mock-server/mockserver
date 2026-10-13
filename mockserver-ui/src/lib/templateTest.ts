/**
 * Client for MockServer's PUT /mockserver/template/test: renders a response or forward template
 * against a sample request without creating an expectation, and reports the rendered output, the
 * failure with its template line and column, and warnings.
 */
import { buildBaseUrl } from './mcpClient';
import type { ConnectionParams } from '../hooks/useConnectionParams';

export type TemplateTestEngine = 'VELOCITY' | 'MUSTACHE' | 'JAVASCRIPT';
export type TemplateTestActionType = 'RESPONSE_TEMPLATE' | 'FORWARD_TEMPLATE';

export interface TemplateTestInput {
  templateType: TemplateTestEngine;
  template: string;
  actionType: TemplateTestActionType;
  /** The sample request; the server uses GET / when it is absent. */
  request?: Record<string, unknown>;
}

export interface TemplateTestError {
  message?: string;
  messageTruncated?: boolean;
  line?: number;
  column?: number;
  hint?: string;
  hintTruncated?: boolean;
  validationErrors?: string;
  validationErrorsTruncated?: boolean;
}

export interface TemplateTestWarning {
  message: string;
  messageTruncated?: boolean;
  line?: number;
  column?: number;
}

export interface TemplateTestReport {
  templateType?: string;
  actionType?: string;
  valid: boolean;
  durationMillis?: number;
  renderedResponse?: Record<string, unknown>;
  renderedRequest?: Record<string, unknown>;
  renderedOutput?: string;
  renderedOutputTruncated?: boolean;
  timedOut: boolean;
  error?: TemplateTestError;
  warnings: TemplateTestWarning[];
}

/** A refusal the server explained: 400 (invalid input), 429 (too many dry runs running), 401/403. */
export class TemplateTestRefused extends Error {
  constructor(message: string, readonly status: number) {
    super(message);
    this.name = 'TemplateTestRefused';
  }
}

function errorField(text: string): string | undefined {
  try {
    const parsed = JSON.parse(text) as { error?: unknown };
    return typeof parsed.error === 'string' ? parsed.error : undefined;
  } catch {
    return undefined;
  }
}

export async function testTemplate(
  params: ConnectionParams,
  input: TemplateTestInput,
  signal?: AbortSignal,
): Promise<TemplateTestReport> {
  const body: Record<string, unknown> = {
    templateType: input.templateType,
    template: input.template,
    actionType: input.actionType,
  };
  if (input.request) body.request = input.request;
  const res = await fetch(`${buildBaseUrl(params)}/mockserver/template/test`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal,
  });
  const text = await res.text().catch(() => '');
  if (!res.ok) {
    const reason = errorField(text);
    if (res.status === 429) {
      throw new TemplateTestRefused(reason ?? 'Too many template dry runs are already running; try again shortly.', 429);
    }
    if (reason && (res.status === 400 || res.status === 401 || res.status === 403)) {
      throw new TemplateTestRefused(reason, res.status);
    }
    throw new Error(`MockServer returned ${res.status}: ${text}`);
  }
  const report = JSON.parse(text) as Partial<TemplateTestReport>;
  return {
    ...report,
    valid: report.valid === true,
    timedOut: report.timedOut === true,
    warnings: Array.isArray(report.warnings) ? report.warnings : [],
  };
}

export interface TemplateExcerptLine {
  number: number;
  text: string;
  /** True for the line the error points at. */
  failing: boolean;
}

/**
 * The lines around a failure position (1-based), for showing where in the template it failed.
 * Returns an empty list when the line is outside the template.
 */
export function templateExcerpt(template: string, line: number, context = 2): TemplateExcerptLine[] {
  const lines = template.split(/\r\n|\r|\n/);
  if (!Number.isInteger(line) || line < 1 || line > lines.length) return [];
  const first = Math.max(1, line - context);
  const last = Math.min(lines.length, line + context);
  const excerpt: TemplateExcerptLine[] = [];
  for (let n = first; n <= last; n++) {
    excerpt.push({ number: n, text: lines[n - 1] ?? '', failing: n === line });
  }
  return excerpt;
}

// Velocity's parser counts a tab as a move to the next multiple-of-8 column, so the excerpt expands
// tabs to the same stops and a Velocity column then needs no conversion.
const TAB_SIZE = 8;

/** The line with each tab replaced by spaces up to the next tab stop. */
export function expandTabs(text: string): string {
  let out = '';
  for (const ch of text) {
    out += ch === '\t' ? ' '.repeat(TAB_SIZE - (out.length % TAB_SIZE)) : ch;
  }
  return out;
}

/**
 * How many spaces go before a caret under `column` (1-based) of `text` once its tabs are expanded.
 * Velocity reports columns with tabs already expanded; other engines count characters.
 */
export function caretOffset(text: string, column: number, templateType: TemplateTestEngine): number {
  if (templateType === 'VELOCITY') return column - 1;
  return expandTabs(text.slice(0, column - 1)).length + Math.max(0, column - 1 - text.length);
}

/** The recorded request of a Received Requests row, or undefined if the row holds none. */
export function recordedRequestOf(value: Record<string, unknown>): Record<string, unknown> | undefined {
  const request = value['httpRequest'];
  return request && typeof request === 'object' && !Array.isArray(request)
    ? (request as Record<string, unknown>)
    : undefined;
}
