import { describe, it, expect } from 'vitest';
import {
  parseRequestScope,
  isApplicableScope,
  globToRegexSource,
  withHostHeaderRow,
  withHostHeaderLine,
  REQUEST_SCOPE_FIELDS,
  BREAKPOINT_SCOPE_FIELDS,
} from '../lib/filterScope';
import { matchesItemSearch } from '../lib/filterDSL';
import { verificationStatusFor, statusRangeProblem, VERIFICATION_SCOPE_FIELDS } from '../lib/filterScope';

describe('filterScope — the operators a server-side matcher can honour', () => {
  it('declares exactly method, path and host', () => {
    expect([...REQUEST_SCOPE_FIELDS]).toEqual(['method', 'path', 'host']);
  });

  it('translates method, path and host into matcher fields', () => {
    const parsed = parseRequestScope('method:post path:/api/orders host:api.example.com');
    expect(parsed.error).toBeNull();
    expect(parsed.unsupportedFields).toEqual([]);
    expect(parsed.scope).toEqual({
      method: 'POST',
      path: '/api/orders',
      host: 'api\\.example\\.com',
    });
    expect(isApplicableScope(parsed)).toBe(true);
  });

  it('reports a registered operator outside the scope vocabulary instead of dropping it', () => {
    for (const term of ['status:>=400', 'operation:GetUser']) {
      const parsed = parseRequestScope(term);
      expect(parsed.unsupportedFields.length).toBeGreaterThan(0);
      expect(parsed.scope).toEqual({});
      expect(isApplicableScope(parsed)).toBe(false);
    }
  });

  it('refuses leftover free text rather than silently ignoring it', () => {
    const parsed = parseRequestScope('method:GET orders');
    expect(parsed.error).toContain('orders');
    expect(parsed.scope).toEqual({});
    expect(isApplicableScope(parsed)).toBe(false);
  });

  it('refuses a numeric comparator, which no scope operator supports', () => {
    const parsed = parseRequestScope('path:>=/api');
    expect(parsed.error).toContain('comparators');
    expect(isApplicableScope(parsed)).toBe(false);
  });

  it('refuses a wildcard on method, which matches one exact method', () => {
    const parsed = parseRequestScope('method:GE*');
    expect(parsed.error).toContain('method:');
    expect(isApplicableScope(parsed)).toBe(false);
  });

  it('refuses an operator with no value', () => {
    expect(parseRequestScope('path:').error).toContain('no value');
  });

  it('is not applicable when the term is empty', () => {
    const parsed = parseRequestScope('   ');
    expect(parsed.error).toBeNull();
    expect(isApplicableScope(parsed)).toBe(false);
  });
});

describe('filterScope — glob to server-side regex', () => {
  it('compiles * to .* and escapes every other metacharacter', () => {
    expect(globToRegexSource('/api/*')).toBe('/api/.*');
    expect(globToRegexSource('/api/v1.0')).toBe('/api/v1\\.0');
    expect(globToRegexSource('*.example.com')).toBe('.*\\.example\\.com');
    expect(globToRegexSource('/a+b(c)')).toBe('/a\\+b\\(c\\)');
  });

  /**
   * The point of the translation: a term must select the same requests in a search
   * box (client-side glob) as in a matcher (server-side full-match regex). MockServer
   * full-matches a regex path, which `new RegExp('^' + source + '$')` models.
   */
  it('selects the same paths as the search box does', () => {
    const cases: Array<[string, string, boolean]> = [
      ['/api/*', '/api/orders', true],
      ['/api/*', '/api/', true],
      ['/api/*', '/other/orders', false],
      ['/api/v1.0', '/api/v1.0', true],
      ['/api/v1.0', '/api/v1x0', false],
    ];
    for (const [glob, path, expected] of cases) {
      const viaSearchBox = matchesItemSearch({ httpRequest: { path } }, `path:${glob}`);
      const viaMatcher = new RegExp(`^${globToRegexSource(glob)}$`).test(path);
      expect(viaSearchBox, `search box ${glob} vs ${path}`).toBe(expected);
      expect(viaMatcher, `matcher ${glob} vs ${path}`).toBe(expected);
    }
  });

  /**
   * Guards the reason the translation exists: passing the raw glob through as a
   * regex would NOT match, silently. If this ever starts passing, the translation
   * has become a no-op and the two surfaces have diverged.
   */
  it('is not a no-op — the untranslated glob would match the wrong set', () => {
    expect(new RegExp('^/api/*$').test('/api/orders')).toBe(false);
    expect(new RegExp(`^${globToRegexSource('/api/*')}$`).test('/api/orders')).toBe(true);
  });
});

describe('filterScope — host lands in the headers matcher', () => {
  it('fills the first blank row, then replaces the Host row on a second apply', () => {
    const first = withHostHeaderRow([{ name: '', values: [''] }], 'api\\.example\\.com');
    expect(first).toEqual([{ name: 'Host', values: ['api\\.example\\.com'] }]);
    expect(withHostHeaderRow(first, 'other.svc')).toEqual([{ name: 'Host', values: ['other.svc'] }]);
  });

  it('replaces an existing Host row case-insensitively and keeps other rows', () => {
    const rows = [
      { name: 'X-Trace', values: ['abc'] },
      { name: 'host', values: ['old.svc'] },
    ];
    expect(withHostHeaderRow(rows, 'new.svc')).toEqual([
      { name: 'X-Trace', values: ['abc'] },
      { name: 'host', values: ['new.svc'] },
    ]);
  });

  it('appends when every row is already in use', () => {
    const rows = [{ name: 'X-Trace', values: ['abc'] }];
    expect(withHostHeaderRow(rows, 'new.svc')).toEqual([
      { name: 'X-Trace', values: ['abc'] },
      { name: 'Host', values: ['new.svc'] },
    ]);
  });

  it('sets the Host line in a headers textarea without disturbing the others', () => {
    expect(withHostHeaderLine('', 'api.example.com')).toBe('Host: api.example.com');
    expect(withHostHeaderLine('X-Trace: abc', 'api.example.com')).toBe('X-Trace: abc\nHost: api.example.com');
    expect(withHostHeaderLine('X-Trace: abc\nhost: old.svc\nX-B: 1', 'new.svc'))
      .toBe('X-Trace: abc\nHost: new.svc\nX-B: 1');
  });

  it('refuses a repeated operator rather than silently keeping the last', () => {
    // The search box ANDs operators, so `path:/a path:/b` matches nothing there.
    // Keeping the last would make one term mean two different things.
    const result = parseRequestScope('path:/a path:/b');
    expect(result.error).toMatch(/path: appears more than once/);
    expect(result.scope).toEqual({});

    expect(parseRequestScope('method:GET method:POST').error).toMatch(/method: appears more than once/);
    expect(parseRequestScope('host:a.svc host:b.svc').error).toMatch(/host: appears more than once/);
  });

  it('still accepts one of each operator together', () => {
    const result = parseRequestScope('method:GET path:/api/* host:api.svc');
    expect(result.error).toBeNull();
    expect(result.scope).toEqual({ method: 'GET', path: '/api/.*', host: 'api\\.svc' });
  });

  it('does not mistake a colon-less line for a Host line', () => {
    expect(withHostHeaderLine('garbage', 'new.svc')).toBe('garbage\nHost: new.svc');
  });

  it('refuses a repeated operator rather than silently keeping the last', () => {
    // matchesItemSearch ANDs operators, so `path:/a path:/b` selects nothing in
    // the search box. Keeping the last would scope the matcher to /b — the same
    // term meaning two different things in the two places.
    const parsed = parseRequestScope('path:/a path:/b');
    expect(parsed.error).toMatch(/path: appears more than once/);
    expect(parsed.scope).toEqual({});

    expect(parseRequestScope('method:GET method:POST').error).toMatch(/method: appears more than once/);
    // A single occurrence of each is still fine.
    expect(parseRequestScope('method:GET path:/a').error).toBeNull();
  });

  it('treats blank lines the same whether appending or replacing', () => {
    // Appending keeps interior blanks (as replacing always did) and drops only the
    // trailing blank a newline-terminated textarea leaves.
    expect(withHostHeaderLine('X-A: 1\n\nX-B: 2', 'new.svc')).toBe('X-A: 1\n\nX-B: 2\nHost: new.svc');
    expect(withHostHeaderLine('X-A: 1\n', 'new.svc')).toBe('X-A: 1\nHost: new.svc');
    expect(withHostHeaderLine('X-A: 1\n\nhost: old\n', 'new.svc')).toBe('X-A: 1\n\nHost: new.svc\n');
  });
});

describe('filterScope — status: on the breakpoint scope', () => {
  const breakpoint = (term: string) => parseRequestScope(term, BREAKPOINT_SCOPE_FIELDS);

  it('stays unsupported on the plain request scope, which has no status range', () => {
    const parsed = parseRequestScope('status:>=400');
    expect(parsed.unsupportedFields).toEqual(['status']);
    expect(isApplicableScope(parsed)).toBe(false);
  });

  it('translates each comparator into an inclusive range', () => {
    expect(breakpoint('status:>=400').scope).toEqual({ statusMin: 400 });
    expect(breakpoint('status:>399').scope).toEqual({ statusMin: 400 });
    expect(breakpoint('status:<=499').scope).toEqual({ statusMax: 499 });
    expect(breakpoint('status:<500').scope).toEqual({ statusMax: 499 });
    expect(breakpoint('status:404').scope).toEqual({ statusMin: 404, statusMax: 404 });
    expect(breakpoint('status:=404').scope).toEqual({ statusMin: 404, statusMax: 404 });
    expect(isApplicableScope(breakpoint('status:>=400'))).toBe(true);
  });

  it('intersects repeated status: operators, as the search box ANDs them', () => {
    expect(breakpoint('status:>=400 status:<500').scope).toEqual({ statusMin: 400, statusMax: 499 });
    // The tighter bound wins whichever operator comes last.
    expect(breakpoint('status:>=400 status:>=500').scope).toEqual({ statusMin: 500 });
    expect(breakpoint('status:>=500 status:>=400').scope).toEqual({ statusMin: 500 });
    expect(breakpoint('status:<=404 status:<500').scope).toEqual({ statusMax: 404 });
  });

  it('combines with the request operators', () => {
    expect(breakpoint('method:get path:/api/* status:>=500').scope).toEqual({
      method: 'GET',
      path: '/api/.*',
      statusMin: 500,
    });
  });

  it('refuses a status that is not a whole number', () => {
    for (const term of ['status:4xx', 'status:>=4.5', 'status:', 'status:>=', 'status:1000', 'status:-1']) {
      const parsed = breakpoint(term);
      expect(parsed.error, term).toMatch(/whole number/);
      expect(parsed.scope, term).toEqual({});
    }
  });

  it('refuses a range that selects no status code', () => {
    for (const term of ['status:>=500 status:<400', 'status:<0', 'status:>999', 'status:404 status:405']) {
      const parsed = breakpoint(term);
      expect(parsed.error, term).toMatch(/selects no status code/);
      expect(parsed.scope, term).toEqual({});
    }
  });

  /**
   * The search box and the registered matcher must select the same responses, so
   * every translated range is checked against matchesItemSearch for every status.
   */
  it('selects exactly the statuses the search box selects', () => {
    const terms = [
      'status:>=400', 'status:>399', 'status:<=499', 'status:<500', 'status:404',
      'status:=201', 'status:>=400 status:<500', 'status:>200 status:<=204',
      'status:>=500 status:>=400', 'status:<=404 status:<500',
    ];
    for (const term of terms) {
      const { statusMin, statusMax } = breakpoint(term).scope;
      for (let status = 100; status <= 599; status++) {
        const viaSearchBox = matchesItemSearch({ httpResponse: { statusCode: status } }, term);
        const viaMatcher = (statusMin == null || status >= statusMin) && (statusMax == null || status <= statusMax);
        expect(viaMatcher, `${term} vs ${status}`).toBe(viaSearchBox);
      }
    }
  });
});

/**
 * The server's StatusCodeMatcher: an exact code when no range is set, else a class such as
 * "4XX" or one numeric comparison. A range it cannot parse matches nothing.
 */
function serverStatusMatches(statusCode: string, range: string, actual: number): boolean {
  if (range.trim() === '') return statusCode === '' || Number(statusCode) === actual;
  const cls = /^\s*([0-9])[xX]{2}\s*$/.exec(range);
  if (cls) return actual >= Number(cls[1]) * 100 && actual <= Number(cls[1]) * 100 + 99;
  const cmp = /^\s*(>=|<=|==|>|<)\s*(-?\d+(?:\.\d+)?)\s*$/.exec(range);
  if (!cmp) return false;
  const n = Number(cmp[2]);
  switch (cmp[1]) {
    case '>=': return actual >= n;
    case '<=': return actual <= n;
    case '>': return actual > n;
    case '<': return actual < n;
    default: return actual === n;
  }
}

describe('verificationStatusFor', () => {
  const verification = (term: string) => parseRequestScope(term, VERIFICATION_SCOPE_FIELDS);

  it('selects exactly the statuses the search box selects, wherever it can express them', () => {
    const terms = [
      'status:>=400', 'status:>399', 'status:<=499', 'status:<500', 'status:404', 'status:=201',
      'status:>=400 status:<500', 'status:>=500 status:<=599', 'status:>=500 status:>=400',
      'status:<=404 status:<500', 'status:>=0', 'status:<=999', 'status:>=0 status:<300',
    ];
    for (const term of terms) {
      const status = verificationStatusFor(verification(term).scope);
      expect(status && !('error' in status), term).toBe(true);
      const { statusCode, statusCodeRange } = status as { statusCode: string; statusCodeRange: string };
      expect(statusRangeProblem(statusCodeRange), term).toBeNull();
      for (let actual = 100; actual <= 599; actual++) {
        const viaSearchBox = matchesItemSearch({ httpResponse: { statusCode: actual } }, term);
        expect(serverStatusMatches(statusCode, statusCodeRange, actual), `${term} vs ${actual}`).toBe(viaSearchBox);
      }
    }
  });

  it('refuses a range only two comparisons express, rather than widening it', () => {
    for (const term of ['status:>=400 status:<450', 'status:>200 status:<=204', 'status:>=401 status:<=499']) {
      const status = verificationStatusFor(verification(term).scope);
      expect(status && 'error' in status, term).toBe(true);
    }
  });

  it('contributes nothing without a status operator', () => {
    expect(verificationStatusFor(verification('method:GET').scope)).toBeNull();
  });
});
