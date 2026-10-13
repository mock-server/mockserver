/**
 * The subset of RFC 6902 JSON Patch that MockServer's suggested expectation fixes use: add, remove, replace and
 * test. Applies to a deep copy, so the input is never changed; throws on the first operation that cannot apply.
 */
export interface JsonPatchOperation {
  op: 'add' | 'remove' | 'replace' | 'test';
  path: string;
  value?: unknown;
}

/** Thrown when a `test` operation finds a different value: the document changed since the patch was made. */
export class JsonPatchTestFailedError extends Error {
  constructor(public readonly path: string) {
    super(`The value at ${path} is no longer what the patch expects`);
    this.name = 'JsonPatchTestFailedError';
  }
}

type Container = Record<string, unknown> | unknown[];

function parsePointer(path: string): string[] {
  if (path === '') return [];
  if (!path.startsWith('/')) throw new Error(`Invalid JSON pointer: ${path}`);
  return path.slice(1).split('/').map((token) => token.replace(/~1/g, '/').replace(/~0/g, '~'));
}

function isContainer(value: unknown): value is Container {
  return typeof value === 'object' && value !== null;
}

function arrayIndex(array: unknown[], token: string, allowEnd: boolean): number {
  if (allowEnd && token === '-') return array.length;
  if (!/^(0|[1-9][0-9]*)$/.test(token)) throw new Error(`Invalid array index: ${token}`);
  const index = Number(token);
  if (index > array.length || (!allowEnd && index === array.length)) throw new Error(`Array index out of range: ${token}`);
  return index;
}

function getChild(container: Container, token: string): unknown {
  if (Array.isArray(container)) return container[arrayIndex(container, token, false)];
  if (!Object.prototype.hasOwnProperty.call(container, token)) return undefined;
  return container[token];
}

function hasChild(container: Container, token: string): boolean {
  if (Array.isArray(container)) return /^(0|[1-9][0-9]*)$/.test(token) && Number(token) < container.length;
  return Object.prototype.hasOwnProperty.call(container, token);
}

/** Structural equality of two JSON values: object key order is ignored, array order is not. */
export function jsonEqual(a: unknown, b: unknown): boolean {
  if (a === b) return true;
  if (!isContainer(a) || !isContainer(b) || Array.isArray(a) !== Array.isArray(b)) return false;
  if (Array.isArray(a) && Array.isArray(b)) {
    return a.length === b.length && a.every((item, i) => jsonEqual(item, b[i]));
  }
  const aRecord = a as Record<string, unknown>;
  const bRecord = b as Record<string, unknown>;
  const aKeys = Object.keys(aRecord);
  return aKeys.length === Object.keys(bRecord).length
    && aKeys.every((key) => Object.prototype.hasOwnProperty.call(bRecord, key) && jsonEqual(aRecord[key], bRecord[key]));
}

function clone<T>(value: T): T {
  return value === undefined ? value : (JSON.parse(JSON.stringify(value)) as T);
}

export function applyJsonPatch<T>(document: T, operations: readonly JsonPatchOperation[]): T {
  let root: unknown = clone(document);
  for (const operation of operations) {
    const tokens = parsePointer(operation.path);
    if (tokens.length === 0) {
      if (operation.op === 'test') {
        if (!jsonEqual(root, operation.value)) throw new JsonPatchTestFailedError(operation.path);
      } else if (operation.op === 'remove') {
        throw new Error('Cannot remove the whole document');
      } else {
        root = clone(operation.value);
      }
      continue;
    }
    let parent: unknown = root;
    for (const token of tokens.slice(0, -1)) {
      if (!isContainer(parent) || !hasChild(parent, token)) throw new Error(`Path not found: ${operation.path}`);
      parent = getChild(parent, token);
    }
    if (!isContainer(parent)) throw new Error(`Path not found: ${operation.path}`);
    const last = tokens[tokens.length - 1] as string;
    switch (operation.op) {
      case 'test':
        if (!hasChild(parent, last) || !jsonEqual(getChild(parent, last), operation.value)) {
          throw new JsonPatchTestFailedError(operation.path);
        }
        break;
      case 'remove':
        if (!hasChild(parent, last)) throw new Error(`Path not found: ${operation.path}`);
        if (Array.isArray(parent)) parent.splice(arrayIndex(parent, last, false), 1);
        else delete parent[last];
        break;
      case 'replace':
        if (!hasChild(parent, last)) throw new Error(`Path not found: ${operation.path}`);
        if (Array.isArray(parent)) parent[arrayIndex(parent, last, false)] = clone(operation.value);
        else parent[last] = clone(operation.value);
        break;
      case 'add':
        if (Array.isArray(parent)) parent.splice(arrayIndex(parent, last, true), 0, clone(operation.value));
        else parent[last] = clone(operation.value);
        break;
      default:
        throw new Error(`Unsupported JSON patch operation: ${(operation as { op: string }).op}`);
    }
  }
  return root as T;
}
