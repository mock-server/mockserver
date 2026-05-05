import { describe, it, expect, beforeEach } from 'vitest';
import { renderHook } from '@testing-library/react';
import { useConnectionParams } from '../hooks/useConnectionParams';

describe('useConnectionParams', () => {
  beforeEach(() => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hostname: 'localhost',
        port: '1080',
        protocol: 'http:',
        search: '',
      },
    });
  });

  it('uses window.location defaults when no query params', () => {
    const { result } = renderHook(() => useConnectionParams());
    expect(result.current.host).toBe('localhost');
    expect(result.current.port).toBe('1080');
    expect(result.current.secure).toBe(false);
  });

  it('extracts host and port from query parameters', () => {
    window.location.search = '?host=example.com&port=9090';
    const { result } = renderHook(() => useConnectionParams());
    expect(result.current.host).toBe('example.com');
    expect(result.current.port).toBe('9090');
  });

  it('detects secure protocol', () => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hostname: 'secure.host',
        port: '443',
        protocol: 'https:',
        search: '',
      },
    });
    const { result } = renderHook(() => useConnectionParams());
    expect(result.current.secure).toBe(true);
  });

  it('defaults port to 443 for https when no port specified', () => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hostname: 'secure.host',
        port: '',
        protocol: 'https:',
        search: '',
      },
    });
    const { result } = renderHook(() => useConnectionParams());
    expect(result.current.port).toBe('443');
  });

  it('defaults port to 80 for http when no port specified', () => {
    Object.defineProperty(window, 'location', {
      writable: true,
      value: {
        hostname: 'example.com',
        port: '',
        protocol: 'http:',
        search: '',
      },
    });
    const { result } = renderHook(() => useConnectionParams());
    expect(result.current.port).toBe('80');
  });
});
