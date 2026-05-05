import { describe, it, expect, beforeEach } from 'vitest';
import { useDashboardStore } from '../store';
import type { WebSocketMessage } from '../types';

describe('DashboardStore', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      logMessages: [],
      activeExpectations: [],
      recordedRequests: [],
      proxiedRequests: [],
      requestFilter: {},
      filterEnabled: false,
      filterExpanded: false,
      connectionStatus: 'disconnected',
      autoScroll: true,
      logSearch: '',
      expectationSearch: '',
      receivedSearch: '',
      proxiedSearch: '',
      error: null,
    });
  });

  describe('applyMessage', () => {
    it('replaces all entity arrays from a WebSocket message', () => {
      const message: WebSocketMessage = {
        logMessages: [{ key: 'log1', value: { messageParts: [] } }],
        activeExpectations: [{ key: 'exp1', value: { httpRequest: { path: '/test' } } }],
        recordedRequests: [{ key: 'rec1', value: { path: '/received' } }],
        proxiedRequests: [{ key: 'prx1', value: { path: '/proxied' } }],
      };

      useDashboardStore.getState().applyMessage(message);
      const state = useDashboardStore.getState();

      expect(state.logMessages).toHaveLength(1);
      expect(state.logMessages[0]!.key).toBe('log1');
      expect(state.activeExpectations).toHaveLength(1);
      expect(state.activeExpectations[0]!.key).toBe('exp1');
      expect(state.recordedRequests).toHaveLength(1);
      expect(state.proxiedRequests).toHaveLength(1);
    });

    it('defaults to empty arrays when fields are missing', () => {
      const message = {} as WebSocketMessage;
      useDashboardStore.getState().applyMessage(message);
      const state = useDashboardStore.getState();

      expect(state.logMessages).toEqual([]);
      expect(state.activeExpectations).toEqual([]);
      expect(state.recordedRequests).toEqual([]);
      expect(state.proxiedRequests).toEqual([]);
    });

    it('sets error from message', () => {
      const message: WebSocketMessage = {
        logMessages: [],
        activeExpectations: [],
        recordedRequests: [],
        proxiedRequests: [],
        error: 'invalid filter',
      };

      useDashboardStore.getState().applyMessage(message);
      expect(useDashboardStore.getState().error).toBe('invalid filter');
    });

    it('clears error when no error in message', () => {
      useDashboardStore.setState({ error: 'old error' });
      useDashboardStore.getState().applyMessage({
        logMessages: [],
        activeExpectations: [],
        recordedRequests: [],
        proxiedRequests: [],
      });
      expect(useDashboardStore.getState().error).toBeNull();
    });
  });

  describe('clearUI', () => {
    it('empties all entity arrays and clears error', () => {
      useDashboardStore.setState({
        logMessages: [{ key: 'l', value: {} }],
        activeExpectations: [{ key: 'e', value: {} }],
        recordedRequests: [{ key: 'r', value: {} }],
        proxiedRequests: [{ key: 'p', value: {} }],
        error: 'some error',
      });

      useDashboardStore.getState().clearUI();
      const state = useDashboardStore.getState();

      expect(state.logMessages).toEqual([]);
      expect(state.activeExpectations).toEqual([]);
      expect(state.recordedRequests).toEqual([]);
      expect(state.proxiedRequests).toEqual([]);
      expect(state.error).toBeNull();
    });
  });

  describe('filter state', () => {
    it('setFilterEnabled updates enabled flag', () => {
      useDashboardStore.getState().setFilterEnabled(true);
      expect(useDashboardStore.getState().filterEnabled).toBe(true);
    });

    it('toggleFilterExpanded toggles the flag', () => {
      expect(useDashboardStore.getState().filterExpanded).toBe(false);
      useDashboardStore.getState().toggleFilterExpanded();
      expect(useDashboardStore.getState().filterExpanded).toBe(true);
      useDashboardStore.getState().toggleFilterExpanded();
      expect(useDashboardStore.getState().filterExpanded).toBe(false);
    });

    it('setRequestFilter updates the filter', () => {
      useDashboardStore.getState().setRequestFilter({ method: 'GET', path: '/api' });
      expect(useDashboardStore.getState().requestFilter).toEqual({ method: 'GET', path: '/api' });
    });
  });

  describe('UI state', () => {
    it('toggleAutoScroll toggles the flag', () => {
      expect(useDashboardStore.getState().autoScroll).toBe(true);
      useDashboardStore.getState().toggleAutoScroll();
      expect(useDashboardStore.getState().autoScroll).toBe(false);
    });

    it('setConnectionStatus updates the status', () => {
      useDashboardStore.getState().setConnectionStatus('connected');
      expect(useDashboardStore.getState().connectionStatus).toBe('connected');
    });

    it('search setters update their respective fields', () => {
      useDashboardStore.getState().setLogSearch('error');
      useDashboardStore.getState().setExpectationSearch('path');
      useDashboardStore.getState().setReceivedSearch('POST');
      useDashboardStore.getState().setProxiedSearch('forward');

      const state = useDashboardStore.getState();
      expect(state.logSearch).toBe('error');
      expect(state.expectationSearch).toBe('path');
      expect(state.receivedSearch).toBe('POST');
      expect(state.proxiedSearch).toBe('forward');
    });
  });

  describe('theme', () => {
    it('toggleThemeMode switches between dark and light', () => {
      useDashboardStore.setState({ themeMode: 'dark' });
      useDashboardStore.getState().toggleThemeMode();
      expect(useDashboardStore.getState().themeMode).toBe('light');
      useDashboardStore.getState().toggleThemeMode();
      expect(useDashboardStore.getState().themeMode).toBe('dark');
    });
  });
});
