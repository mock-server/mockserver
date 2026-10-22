export interface RequestDefinitionDescription {
  json: false;
  first: string;
  second: string;
}

export interface RequestDefinitionObjectDescription {
  json: true;
  first: string;
  object: Record<string, unknown>;
  second: string;
}

export type Description =
  | string
  | RequestDefinitionDescription
  | RequestDefinitionObjectDescription;

/**
 * The server shortens long bodies in dashboard updates. This says which body was cut, how long it is
 * in full and how much is shown (both in characters), and which log entry to load it from
 * (GET /mockserver/logEntryBody). An expectation shown in a log message, or a request or response argument
 * that is not the entry's own, can be cut but not loaded (`loadable: false`).
 */
export interface TruncatedBody {
  logEntryId: string;
  part: 'request' | 'response' | 'expectation';
  originalLength: number;
  shownLength: number;
  /** False when this is not the log entry's own request or response, so it cannot be loaded from the entry. */
  loadable?: boolean;
}

export interface MessagePart {
  key: string;
  value: string | string[] | Record<string, unknown> | number;
  argument?: boolean;
  json?: boolean;
  multiline?: boolean;
  because?: boolean;
  truncatedBody?: TruncatedBody;
}

export interface LogEntryValue {
  description?: Description;
  style?: Record<string, string>;
  messageParts?: MessagePart[];
  /**
   * Server-side capture time of the log entry, as the raw string MockServer
   * formats it (`yyyy-MM-dd HH:mm:ss.SSS`). Optional: older servers — and the
   * current dashboard WebSocket serializer — may omit it, in which case no time
   * is rendered. When present, the row shows a compact time with the full
   * timestamp on hover.
   */
  timestamp?: string;
}

export interface LogEntry {
  key: string;
  value: LogEntryValue;
  group?: false;
}

export interface LogGroup {
  key: string;
  /** Shared by every entry in the group; absent from servers older than the Explain action. */
  correlationId?: string;
  group: LogEntry;
  value: LogEntry[];
}

export type LogMessage = LogEntry | LogGroup;

export function isLogGroup(message: LogMessage): message is LogGroup {
  return 'group' in message && message.group !== undefined && message.group !== false;
}

export interface JsonListItem {
  key: string;
  description?: Description;
  value: Record<string, unknown>;
  /**
   * When the entry was recorded, for the request sections. Shown in place of a
   * row ordinal: these lists are a capped live window, so a position within one
   * renumbers on every push and refers to nothing.
   */
  timestamp?: string;
  /**
   * The request's log correlation id, on the request sections. A received row and
   * the proxied row of the same request share it (see combineTraffic).
   */
  correlationId?: string;
  /** The row's request or response bodies the server shortened; absent when both were sent whole. */
  truncatedBodies?: {
    httpRequest?: TruncatedBody;
    httpResponse?: TruncatedBody;
  };
  /**
   * Set on an Active Expectations item whose long bodies (or other long strings) the server shortened.
   * The value must never be edited or saved as it is: load the whole expectation by id first.
   */
  truncatedExpectation?: TruncatedExpectation;
}

export interface TruncatedExpectation {
  expectationId: string;
  part: 'expectation';
  /** The longest shortened value's full length, in characters. */
  originalLength: number;
  shownLength: number;
}

export interface WebSocketMessage {
  logMessages: LogMessage[];
  activeExpectations: JsonListItem[];
  /** Total expectations held by the server; `activeExpectations` is a capped page of them. */
  activeExpectationsTotal?: number;
  /**
   * Whether ANY expectation the server holds is an LLM expectation — computed
   * server-side over the WHOLE matcher set, not the capped `activeExpectations`
   * page. The dashboard offers its LLM Provider filter from this: because the
   * page is capped, a server with many non-LLM expectations ahead of its LLM
   * ones would never send an LLM expectation in the page, so the page alone
   * cannot reveal that one exists. Absent from an older server that predates the
   * signal — the client then falls back to inspecting the page (see FilterPanel).
   */
  activeExpectationsIncludeLlm?: boolean;
  recordedRequests: JsonListItem[];
  proxiedRequests: JsonListItem[];
  /** The update reached its size limit, so older request rows were left out of it. */
  frameLimitReached?: boolean;
  /** The log messages reached their share of the update's size limit, so older log messages were left out. */
  logMessagesLimitReached?: boolean;
  error?: string;
}

/**
 * A MockServer STRING body matcher DTO. `subString: true` makes the server-side
 * matcher do a contains-match rather than full-body equality — see the
 * BodyDTODeserializer, which builds `new StringBody(string, …, subString, …)`.
 * A bare string would deserialize to `subString=false` (exact match), so the
 * "Body contains" filter must send this object form with `subString: true`.
 */
export interface StringBodyMatcher {
  type: 'STRING';
  string: string;
  subString: boolean;
}

export interface RequestFilter {
  method?: string;
  path?: string;
  /**
   * Request-body matcher. The "Body contains" filter ships a STRING body with
   * `subString: true` so the server does substring (contains) matching, not
   * full-body equality.
   */
  body?: StringBodyMatcher;
  keepAlive?: boolean;
  secure?: boolean;
  headers?: KeyToMultiValue[];
  queryStringParameters?: KeyToMultiValue[];
  cookies?: KeyToValue[];
}

export interface KeyToMultiValue {
  name: string;
  values: string[];
}

export interface KeyToValue {
  name: string;
  value: string;
}

export type ConnectionStatus = 'disconnected' | 'connecting' | 'connected' | 'error';

export type ThemeMode = 'dark' | 'light';

export type ClearType = 'all' | 'log' | 'expectations';

export interface DebugMismatchExpectationResult {
  expectationId?: string;
  expectationPath?: string;
  expectationMethod?: string;
  matches: boolean;
  matchedFieldCount: number;
  totalFieldCount: number;
  differences?: Record<string, string[]>;
  /** An anyOf request matcher that does not match: which branch came closest; differences are that branch's. */
  anyOf?: {
    closestBranch: number;
    branches: { branch: number; matches: boolean; differingFieldCount: number; differingFields: string[] }[];
  };
}

export interface DebugMismatchClosestMatch {
  expectationId: string;
  matchedFields: number;
  totalFields: number;
}

export interface DebugMismatchResult {
  correlationId: string;
  timestamp: string;
  totalExpectations: number;
  evaluatedExpectations: number;
  truncated?: boolean;
  maxExpectationsEvaluated?: number;
  closestMatch?: DebugMismatchClosestMatch;
  results: DebugMismatchExpectationResult[];
  /** The original unmatched request — attached client-side so the UI can
   *  offer a "Create Expectation" action from the mismatch dialog. */
  unmatchedRequest?: Record<string, unknown>;
}
