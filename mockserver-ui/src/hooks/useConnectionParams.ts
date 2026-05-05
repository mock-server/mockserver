import { useMemo } from 'react';

export interface ConnectionParams {
  host: string;
  port: string;
  secure: boolean;
}

export function useConnectionParams(): ConnectionParams {
  return useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    const host =
      params.get('host') || window.location.hostname || '127.0.0.1';
    const port =
      params.get('port') ||
      window.location.port ||
      (window.location.protocol === 'https:' ? '443' : '80');
    const secure = window.location.protocol === 'https:';
    return { host, port, secure };
  }, []);
}
