import { describe, it, expect, beforeEach } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import LogPanel from '../components/LogPanel';
import { useDashboardStore } from '../store';

/**
 * THE ORIGINAL COMPLAINT, in its most natural form: "when I open an item the
 * panel still refreshes and I can't read that item."
 *
 * In these panels a row expands INLINE, which changes Panel's `children` and so
 * re-fires the tail pin. While following, that scrolls the row the reader just
 * opened up and away; and because the hold is gated on `!follow`, the row is not
 * held either, so within ~10s at ~10 req/s the capped window drops it and the
 * expansion vanishes outright.
 *
 * Opening a row therefore stops following, which suppresses the tail pin and
 * engages the hold in one move. (TrafficInspector is deliberately different:
 * opening a row there is a SELECTION that renders a separate detail pane, so
 * scrolling the list cannot move what is being read.)
 */
describe('opening a row stops the panel following', () => {
  beforeEach(() => {
    useDashboardStore.setState({
      autoScroll: true,
      logMessages: [
        { key: 'log1', value: { messageParts: [{ key: 'm1', value: 'first entry body' }] } },
        { key: 'log2', value: { messageParts: [{ key: 'm2', value: 'second entry body' }] } },
      ],
      logSearch: '',
      logShowForwarded: true,
    });
  });

  it('flips Follow off when the reader expands an entry, and holds it open', async () => {
    const user = userEvent.setup();
    render(<LogPanel />);

    // Starts following, pinned to the newest entry at the bottom.
    expect(screen.getByText('Following')).toBeInTheDocument();

    await user.click(screen.getAllByLabelText('Expand')[0]!);

    // Following has stopped, so the tail pin cannot scroll the opened row away
    // and useHeldItems is now holding. The control says so rather than quietly
    // doing something else.
    expect(screen.getByText('Follow')).toBeInTheDocument();
    expect(screen.queryByText('Following')).not.toBeInTheDocument();

    // The row the reader opened survives a push that evicts it from the window.
    // NOTE: this proves the row is still in the DOM and expanded, NOT that it is
    // on screen; jsdom has no scroll or windowing. e2e/dashboard-live-scroll.spec.ts
    // covers the on-screen invariant in a real browser.
    act(() => {
      useDashboardStore.getState().applyMessage({
        logMessages: [
          { key: 'log9', value: { messageParts: [{ key: 'm9', value: 'brand new entry' }] } },
        ],
        activeExpectations: [],
        recordedRequests: [],
        proxiedRequests: [],
      });
    });
    expect(screen.getByText('brand new entry')).toBeInTheDocument();
    expect(screen.getByLabelText('Collapse')).toBeInTheDocument();
  });

  it('keeps following when the reader collapses without opening anything else', async () => {
    const user = userEvent.setup();
    render(<LogPanel />);

    await user.click(screen.getAllByLabelText('Expand')[0]!);
    expect(screen.getByText('Follow')).toBeInTheDocument();

    // Collapsing does not silently resume following — the reader resumes it
    // deliberately, so the panel never starts moving again unannounced.
    await user.click(screen.getByLabelText('Collapse'));
    expect(screen.getByText('Follow')).toBeInTheDocument();
  });
});
