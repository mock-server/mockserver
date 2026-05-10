import { createContext, useContext } from 'react';

export type DebugMismatchFn = (request: Record<string, unknown>) => Promise<void>;

export const DebugMismatchContext = createContext<DebugMismatchFn | null>(null);

export function useDebugMismatchContext(): DebugMismatchFn | null {
  return useContext(DebugMismatchContext);
}
