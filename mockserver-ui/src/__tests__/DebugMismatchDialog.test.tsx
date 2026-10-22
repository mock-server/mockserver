import { describe, it, expect, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useDashboardStore } from '../store';
import DebugMismatchDialog from '../components/DebugMismatchDialog';
import type { DebugMismatchResult } from '../types';

const connectionParams = { host: 'localhost', port: '1080', secure: false };

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
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.queryByText("Why Didn't This Match?")).not.toBeInTheDocument();
  });

  it('shows title and expectation count when open with results', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText("Why Didn't This Match?")).toBeInTheDocument();
    expect(screen.getByText('2 expectations')).toBeInTheDocument();
  });

  it('shows loading message', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchLoading: true,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText('Analyzing match results...')).toBeInTheDocument();
  });

  it('shows error message', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchError: 'Connection failed',
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText('Connection failed')).toBeInTheDocument();
  });

  it('shows an honest "differs on N field(s)" badge counting only failing fields', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    // exp-1 differs on path + body; exp-2 differs on method only. The badge counts
    // failing fields, not (all-fields − failing), so it never inflates the score.
    expect(screen.getByText('differs on 2 fields')).toBeInTheDocument();
    expect(screen.getByText('differs on 1 field')).toBeInTheDocument();
    expect(screen.queryByText('10/12')).not.toBeInTheDocument();
  });

  it('does not inflate the score for a matcher that only specifies a couple of fields', () => {
    // A method+path-only matcher: the server reports 15 of 16 possible fields as
    // "matched" (14 were never specified), which previously read as 15/16. The
    // honest count is the single field that actually differs.
    const inflated: DebugMismatchResult = {
      ...sampleResult,
      closestMatch: undefined,
      results: [
        {
          expectationId: 'exp-x',
          expectationMethod: 'GET',
          expectationPath: '/api/users',
          matches: false,
          matchedFieldCount: 15,
          totalFieldCount: 16,
          differences: { path: ['expected /api/users but was /api/items'] },
        },
      ],
    };
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: inflated,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText('differs on 1 field')).toBeInTheDocument();
    expect(screen.queryByText('15/16')).not.toBeInTheDocument();
  });

  it('gives the close icon button an accessible label', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(document.querySelector('button[aria-label="Close"]')).toBeInTheDocument();
  });

  it('renders a client-derived remediation hint on each failing field', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);

    await user.click(screen.getByText('GET /api/users'));
    // Hints mirror the server's MismatchRemediation defaults, derived from the field name.
    expect(screen.getByText(/Hint: check the request path/)).toBeInTheDocument();
    expect(screen.getByText(/Hint: check the request body content/)).toBeInTheDocument();
  });

  it('shows method and path for expectations', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText('GET /api/users')).toBeInTheDocument();
    expect(screen.getByText('POST /api/orders')).toBeInTheDocument();
  });

  it('marks closest match with chip', () => {
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText('closest')).toBeInTheDocument();
  });

  it('expands to show differences when clicked', async () => {
    const user = userEvent.setup();
    useDashboardStore.setState({
      debugMismatchOpen: true,
      debugMismatchResult: sampleResult,
    });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);

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
    render(<DebugMismatchDialog connectionParams={connectionParams} />);

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
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
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
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getByText('No active expectations')).toBeInTheDocument();
  });

  describe('Visual diff tab', () => {
    it('renders DiffPanel rows for the closest expectation differing fields', async () => {
      const user = userEvent.setup();
      useDashboardStore.setState({
        debugMismatchOpen: true,
        debugMismatchResult: sampleResult,
      });
      render(<DebugMismatchDialog connectionParams={connectionParams} />);

      await user.click(screen.getByRole('tab', { name: 'Visual Diff' }));

      // Diff table header from DiffPanel.
      expect(screen.getByText('Expected')).toBeInTheDocument();
      expect(screen.getByText('Actual')).toBeInTheDocument();
      // Field rows derived from the closest match's differences.
      expect(screen.getByText('path')).toBeInTheDocument();
      expect(screen.getByText('body')).toBeInTheDocument();
      // expected-vs-actual extracted from "expected X but was Y".
      expect(screen.getByText('/api/users')).toBeInTheDocument();
      expect(screen.getByText('/api/items')).toBeInTheDocument();
    });

    it('only diffs the closest expectation, not other candidates', async () => {
      const user = userEvent.setup();
      useDashboardStore.setState({
        debugMismatchOpen: true,
        debugMismatchResult: sampleResult,
      });
      render(<DebugMismatchDialog connectionParams={connectionParams} />);

      await user.click(screen.getByRole('tab', { name: 'Visual Diff' }));
      // exp-2's only difference is on `method`; it must NOT appear because exp-1 is closest.
      expect(screen.queryByText('method')).not.toBeInTheDocument();
    });

    it('falls back to the best-scoring candidate when there is no closest match', async () => {
      const user = userEvent.setup();
      useDashboardStore.setState({
        debugMismatchOpen: true,
        debugMismatchResult: { ...sampleResult, closestMatch: undefined },
      });
      render(<DebugMismatchDialog connectionParams={connectionParams} />);

      await user.click(screen.getByRole('tab', { name: 'Visual Diff' }));
      // exp-1 (10/12) outscores exp-2 (6/12), so its fields drive the diff.
      expect(screen.getByText('path')).toBeInTheDocument();
    });

    it('shows a fallback message when no candidate has field-level differences', async () => {
      const user = userEvent.setup();
      const noDiffs: DebugMismatchResult = {
        ...sampleResult,
        closestMatch: undefined,
        results: [
          {
            expectationId: 'exp-1',
            expectationMethod: 'GET',
            expectationPath: '/api/users',
            matches: false,
            matchedFieldCount: 10,
            totalFieldCount: 12,
          },
        ],
      };
      useDashboardStore.setState({
        debugMismatchOpen: true,
        debugMismatchResult: noDiffs,
      });
      render(<DebugMismatchDialog connectionParams={connectionParams} />);

      await user.click(screen.getByRole('tab', { name: 'Visual Diff' }));
      expect(
        screen.getByText(/No closest expectation with field-level differences/),
      ).toBeInTheDocument();
    });

    it('keeps the text reasons available on the Match results tab', async () => {
      const user = userEvent.setup();
      useDashboardStore.setState({
        debugMismatchOpen: true,
        debugMismatchResult: sampleResult,
      });
      render(<DebugMismatchDialog connectionParams={connectionParams} />);

      // Default tab still shows the score/path UI (info not removed).
      expect(screen.getByText('differs on 2 fields')).toBeInTheDocument();
      await user.click(screen.getByText('GET /api/users'));
      expect(
        screen.getByText('expected /api/users but was /api/items'),
      ).toBeInTheDocument();
    });
  });

  it('names the closest branch of an anyOf request matcher', () => {
    const anyOfResult: DebugMismatchResult = {
      ...sampleResult,
      results: [{
        ...sampleResult.results[0]!,
        anyOf: { closestBranch: 1, branches: [
          { branch: 0, matches: false, differingFieldCount: 2, differingFields: ['method', 'path'] },
          { branch: 1, matches: false, differingFieldCount: 1, differingFields: ['path'] },
        ] },
      }, sampleResult.results[1]!],
    };
    useDashboardStore.setState({ debugMismatchOpen: true, debugMismatchResult: anyOfResult });
    render(<DebugMismatchDialog connectionParams={connectionParams} />);
    expect(screen.getAllByTestId('debug-mismatch-any-of-closest')).toHaveLength(1);
    expect(screen.getByText('anyOf: closest is branch 1 of 2')).toBeInTheDocument();
  });
});
