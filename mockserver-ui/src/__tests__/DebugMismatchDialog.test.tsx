import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useDashboardStore } from '../store';
import DebugMismatchDialog from '../components/DebugMismatchDialog';
import type { DebugMismatchResult } from '../types';

const sampleResult: DebugMismatchResult = {
  correlationId: 'abc-123',
  timestamp: '2025-05-10T12:00:00Z',
  totalExpectations: 2,
  evaluatedExpectations: 2,
  closestMatch: { expectationId: 'exp-1', matchedFields: 10, totalFields: 12 },
  results: [
    {
      expectationId: 'exp-1',
      expectationMethod: 'GET',
      expectationPath: '/api/users',
      matches: false,
      matchedFieldCount: 10,
      totalFieldCount: 12,
      differences: {
        path: ['expected /api/users but was /api/items'],
        body: ['expected JSON but was empty'],
      },
    },
    {
      expectationId: 'exp-2',
      expectationMethod: 'POST',
      expectationPath: '/api/orders',
      matches: false,
      matchedFieldCount: 6,
      totalFieldCount: 12,
      differences: {
        method: ['expected POST but was GET'],
      },
    },
  ],
};

beforeEach(() => {
  useDashboardStore.setState({
    debugMismatchOpen: false,
    debugMismatchResult: null,
    debugMismatchLoading: false,
    debugMismatchError: null,
  });
});

describe('DebugMismatchDialog', () => {
  it('does not render content when closed', () => {
    render(<DebugMismatchDialog />);
    expect(screen.queryByText("Why Didn't This Match?")).not.toBeInTheDocument();
  });

  it('shows title and expectation count when open with results', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText("Why Didn't This Match?")).toBeInTheDocument();
    expect(screen.getByText('2 expectations')).toBeInTheDocument();
  });

  it('shows loading message', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchLoading: true,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('Analyzing match results...')).toBeInTheDocument();
  });

  it('shows error message', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchError: 'Connection failed',
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('Connection failed')).toBeInTheDocument();
  });

  it('shows score badges for each expectation', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('10/12')).toBeInTheDocument();
    expect(screen.getByText('6/12')).toBeInTheDocument();
  });

  it('shows method and path for expectations', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('GET /api/users')).toBeInTheDocument();
    expect(screen.getByText('POST /api/orders')).toBeInTheDocument();
  });

  it('marks closest match with chip', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('closest')).toBeInTheDocument();
  });

  it('expands to show differences when clicked', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog />);

    await user.click(screen.getByText('GET /api/users'));
    expect(screen.getByText('path')).toBeInTheDocument();
    expect(screen.getByText('expected /api/users but was /api/items')).toBeInTheDocument();
  });

  it('closes when close button is clicked', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog />);

    await user.click(screen.getByText('Close'));
    const state = useDashboardStore.getState();
    expect(state.debugMismatchOpen).toBe(false);
  });

  it('shows truncation warning', () => {
    const truncatedResult: DebugMismatchResult = {
      ...sampleResult,
      totalExpectations: 150,
      truncated: true,
      maxExpectationsEvaluated: 100,
    };
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: truncatedResult,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('Showing first 100 of 150 expectations')).toBeInTheDocument();
  });

  it('shows empty state when no expectations', () => {
    const emptyResult: DebugMismatchResult = {
      ...sampleResult,
      totalExpectations: 0,
      evaluatedExpectations: 0,
      closestMatch: undefined,
      results: [],
    };
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: emptyResult,
    });
    render(<DebugMismatchDialog />);
    expect(screen.getByText('No active expectations')).toBeInTheDocument();
  });
});
