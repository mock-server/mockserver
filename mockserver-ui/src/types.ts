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

export interface MessagePart {
  key: string;
  value: string | string[] | Record<string, unknown> | number;
  argument?: boolean;
  json?: boolean;
  multiline?: boolean;
  because?: boolean;
}

export interface LogEntryValue {
  description?: Description;
  style?: Record<string, string>;
  messageParts?: MessagePart[];
}

export interface LogEntry {
  key: string;
  value: LogEntryValue;
  group?: false;
}

export interface LogGroup {
  key: string;
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
}

export interface WebSocketMessage {
  logMessages: LogMessage[];
  activeExpectations: JsonListItem[];
  recordedRequests: JsonListItem[];
  proxiedRequests: JsonListItem[];
  error?: string;
}

export interface RequestFilter {
  method?: string;
  path?: string;
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
}
