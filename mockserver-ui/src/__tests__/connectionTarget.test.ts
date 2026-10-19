import { describe, it, expect, vi, afterEach } from 'vitest';
import {
  coerceConnection,
  connectionKey,
  isCrossOrigin,
  isMixedContent,
  pageOriginDashboardUrl,
  probeTarget,
  resolveUrlConnection,
  urlConnectionParams,
  validateConnection,
} from '../lib/connectionTarget';

const page = (protocol: string, hostname: string, port: string) => {
  Object.defineProperty(window, 'location', {
    writable: true,
    configurable: true,
    value: { protocol, hostname, port, search: '', pathname: '/mockserver/dashboard', hash: '' },
  });
};

describe('validateConnection', () => {
  it('accepts a host, port, scheme and base path, normalising them', () => {
    expect(validateConnection({ host: ' Example.com ', port: '01081', secure: true, basePath: '/mock/' })).toEqual({
      ok: true,
      value: { host: 'example.com', port: '1081', secure: true, basePath: '/mock' },
    });
    expect(validateConnection({ host: '[::1]', port: '1080', secure: false, basePath: '' }).ok).toBe(true);
    expect(validateConnection({ host: '10.0.0.7', port: '65535', secure: false, basePath: '' }).ok).toBe(true);
    expect(validateConnection({ host: 'mock_server', port: '1080', secure: false, basePath: '' })).toMatchObject({ ok: true, value: { host: 'mock_server' } });
  });

  it.each([
    ['', '1080', '', 'host'],
    ['http://localhost', '1080', '', 'host'],
    ['localhost/mock', '1080', '', 'host'],
    ['local host', '1080', '', 'host'],
    ['localhost', '0', '', 'port'],
    ['localhost', '70000', '', 'port'],
    ['localhost', '10a', '', 'port'],
    ['localhost', '1080', 'mock', 'basePath'],
    ['localhost', '1080', '/mo ck', 'basePath'],
    ['localhost', '1080', '/mock?x=1', 'basePath'],
    ['localhost', '1080', '/mockserver/reset#', 'basePath'],
    ['localhost', '1080', '/mockserver/reset%23', 'basePath'],
    ['localhost', '1080', '/a/../mockserver/reset', 'basePath'],
    ['localhost', '1080', '/./mock', 'basePath'],
    ['localhost', '1080', '//evil.example', 'basePath'],
    ['localhost', '1080', '/mock\\x', 'basePath'],
    ['localhost', '1080', 'https://evil.example/x', 'basePath'],
    ['user@localhost', '1080', '', 'host'],
    ['localhost#', '1080', '', 'host'],
    ['localhost:1080', '1080', '', 'host'],
  ])('refuses host %j port %j base path %j on the %s field', (host, port, basePath, field) => {
    const result = validateConnection({ host, port, secure: false, basePath });
    expect(result.ok).toBe(false);
    expect(!result.ok && result.field).toBe(field);
  });
});

describe('validateConnection base paths', () => {
  it.each(['/mock', '/api/v2', '/a-b_c.d~e', "/x;v=1/@team/a+b!$&'()*,=:"])('keeps the base path %j', (basePath) => {
    expect(validateConnection({ host: 'h', port: '1080', secure: false, basePath })).toMatchObject({ ok: true, value: { basePath } });
  });
});

describe('coerceConnection', () => {
  it('drops anything that is not a whole, valid target', () => {
    expect(coerceConnection(null)).toBeNull();
    expect(coerceConnection({ host: 'h', port: 1080, secure: false })).toBeNull();
    expect(coerceConnection({ host: 'http://h', port: '1080', secure: false, basePath: '' })).toBeNull();
    expect(coerceConnection({ host: 'h', port: '1080', secure: 'yes', basePath: '' })).toBeNull();
    expect(coerceConnection({ host: 'h', port: '1080', secure: false })).toEqual({ host: 'h', port: '1080', secure: false, basePath: '' });
  });
});

describe('origins', () => {
  afterEach(() => page('http:', 'localhost', '3000'));

  it('treats the same scheme, host and port as same-origin, with default ports folded', () => {
    page('http:', 'localhost', '1080');
    expect(isCrossOrigin({ host: 'localhost', port: '1080', secure: false })).toBe(false);
    expect(isCrossOrigin({ host: 'LOCALHOST', port: '1080', secure: false, basePath: '/x' })).toBe(false);
    expect(isCrossOrigin({ host: 'localhost', port: '1081', secure: false })).toBe(true);
    expect(isCrossOrigin({ host: 'localhost', port: '1080', secure: true })).toBe(true);
    page('https:', 'mock.example', '');
    expect(isCrossOrigin({ host: 'mock.example', port: '443', secure: true })).toBe(false);
  });

  it('flags an http target from an https dashboard as mixed content', () => {
    page('https:', 'mock.example', '');
    expect(isMixedContent({ host: 'other', port: '1080', secure: false })).toBe(true);
    expect(isMixedContent({ host: 'other', port: '1080', secure: true })).toBe(false);
  });

  it('keys a target by everything that selects a server', () => {
    const a = { host: 'h', port: '1', secure: false, basePath: '' };
    expect(connectionKey(a)).toBe(connectionKey({ ...a, host: 'H' }));
    expect(connectionKey(a)).not.toBe(connectionKey({ ...a, basePath: '/x' }));
    expect(connectionKey(a)).not.toBe(connectionKey({ ...a, secure: true }));
  });
});

describe('probeTarget', () => {
  const target = { host: 'other', port: '1081', secure: false, basePath: '/m' };

  afterEach(() => page('http:', 'localhost', '3000'));

  it('calls PUT /mockserver/status under the base path and passes on 200', async () => {
    page('http:', 'localhost', '1080');
    const fetchImpl = vi.fn(async () => new Response('{}', { status: 200 }));
    await expect(probeTarget(target, fetchImpl)).resolves.toEqual({ ok: true });
    expect(fetchImpl).toHaveBeenCalledWith('http://other:1081/m/mockserver/status', expect.objectContaining({ method: 'PUT' }));
  });

  it('names authentication, a wrong path, and CORS or reachability as the causes', async () => {
    page('http:', 'localhost', '1080');
    const auth = await probeTarget(target, vi.fn(async () => new Response('', { status: 401 })));
    expect(!auth.ok && auth.message).toMatch(/control-plane authentication \(HTTP 401\)/);
    const notFound = await probeTarget(target, vi.fn(async () => new Response('', { status: 404 })));
    expect(!notFound.ok && notFound.message).toMatch(/not a MockServer at that base path/);
    const blocked = await probeTarget(target, vi.fn(async () => { throw new TypeError('Failed to fetch'); }));
    expect(!blocked.ok && blocked.message).toMatch(/Could not reach MockServer at other:1081\/m\. Calls to it are cross-origin/);
    expect(!blocked.ok && blocked.message).toMatch(/corsAllowOrigin/);
  });

  it('refuses an http target from an https dashboard without sending anything', async () => {
    page('https:', 'mock.example', '');
    const fetchImpl = vi.fn();
    const result = await probeTarget(target, fetchImpl as unknown as typeof fetch);
    expect(!result.ok && result.message).toMatch(/blocks calls to an http target/);
    expect(fetchImpl).not.toHaveBeenCalled();
  });
});

describe('resolveUrlConnection', () => {
  const at = (search: string, pathname = '/mockserver/dashboard/') => {
    Object.defineProperty(window, 'location', {
      writable: true,
      configurable: true,
      value: { protocol: 'http:', hostname: 'localhost', port: '1080', search, pathname, hash: '', href: `http://localhost:1080${pathname}${search}#/traffic` },
    });
  };
  const PAGE = { host: 'localhost', port: '1080', secure: false, basePath: '' };

  afterEach(() => page('http:', 'localhost', '3000'));

  it('uses the page origin when the link names no target', () => {
    at('');
    expect(resolveUrlConnection()).toMatchObject({ params: PAGE, rejected: [] });
  });

  it('keeps a valid ?host, ?port, ?secure and ?basePath', () => {
    at('?host=Example.com&port=09090&secure=true&basePath=/api/v2/');
    expect(resolveUrlConnection()).toMatchObject({
      params: { host: 'example.com', port: '9090', secure: true, basePath: '/api/v2' },
      rejected: [],
    });
    at('?host=[::1]&port=1081');
    expect(urlConnectionParams()).toMatchObject({ host: '[::1]', port: '1081' });
  });

  it('derives the base path from a dashboard served under a context path', () => {
    at('', '/ctx/mockserver/dashboard');
    expect(resolveUrlConnection()).toMatchObject({ params: { ...PAGE, basePath: '/ctx' }, rejected: [] });
    at('?basePath=', '/ctx/mockserver/dashboard');
    expect(urlConnectionParams().basePath).toBe('');
  });

  it.each([
    ['?basePath=/mockserver/reset%23', 'basePath'],
    ['?basePath=/mockserver/stop%23', 'basePath'],
    ['?basePath=/mockserver/clear%3F', 'basePath'],
    ['?basePath=/mockserver/reset%2523', 'basePath'],
    ['?basePath=/x/../mockserver/reset', 'basePath'],
    ['?basePath=//evil.example', 'basePath'],
    ['?basePath=http://evil.example', 'basePath'],
    ['?basePath=/a%5Cb', 'basePath'],
    ['?host=127.0.0.1:1080/mockserver/stop%23', 'host'],
    ['?host=a/b%23', 'host'],
    ['?host=x@y', 'host'],
    ['?host=http://evil.example', 'host'],
    ['?host=evil.example%23', 'host'],
    ['?port=99999', 'port'],
    ['?port=0', 'port'],
    ['?port=80/mockserver/reset%23', 'port'],
    ['?port=abc', 'port'],
    ['?port=%2B1080', 'port'],
    ['?port=0001080', 'port'],
    ['?basePath=/x/%EF%BC%8E%EF%BC%8E', 'basePath'],
    ['?basePath=/a%EF%BC%8Fb', 'basePath'],
    ['?basePath=/a%09b', 'basePath'],
    ['?basePath=/a%0Ab', 'basePath'],
    ['?basePath=/%2e%2e/mockserver/reset', 'basePath'],
    ['?basePath=/reset%23', 'basePath'],
    ['?host=evil%EF%BC%8Ecom', 'host'],
    ['?host=a%09b', 'host'],
    ['?host=[1.2.3.4]', 'host'],
    ['?host=[:]', 'host'],
    [`?host=${'a'.repeat(10_000)}`, 'host'],
  ])('ignores %s and falls back to the page origin', (search, field) => {
    at(search);
    expect(resolveUrlConnection()).toMatchObject({ params: PAGE, rejected: [field] });
  });

  it('flags a valid host other than the page\'s, but not a different port, scheme or base path', () => {
    at('?host=attacker.example&port=443&secure=true');
    expect(resolveUrlConnection()).toMatchObject({
      params: { host: 'attacker.example', port: '443', secure: true },
      page: PAGE,
      rejected: [],
      foreignHost: true,
    });
    at('?host=LOCALHOST&port=1081&secure=true&basePath=/mock');
    expect(resolveUrlConnection()).toMatchObject({ foreignHost: false, rejected: [] });
    at('?host=x@y');
    expect(resolveUrlConnection()).toMatchObject({ foreignHost: false, rejected: ['host'] });
  });

  it('drops only the target parameters from the address of the page origin\'s dashboard', () => {
    at('?host=attacker.example&port=443&secure=true&basePath=/m&theme=dark', '/ctx/mockserver/dashboard/');
    expect(pageOriginDashboardUrl()).toBe('http://localhost:1080/ctx/mockserver/dashboard/?theme=dark#/traffic');
  });

  it('falls back field by field, keeping the valid values', () => {
    at('?host=other&port=99999&basePath=/mockserver/reset%23');
    expect(resolveUrlConnection()).toMatchObject({ params: { ...PAGE, host: 'other' }, rejected: ['port', 'basePath'] });
  });

  it('refuses an encoded character in the page path rather than using it as the base path', () => {
    at('', '/mockserver/reset%23/mockserver/dashboard/');
    expect(resolveUrlConnection()).toMatchObject({ params: PAGE, rejected: ['basePath'] });
  });
});
