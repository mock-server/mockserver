import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import BecauseSection from '../components/BecauseSection';

describe('BecauseSection', () => {
  const reasons = [
    'method matched',
    "path didn't match",
    'some neutral info',
  ];

  it('renders collapsed by default with ellipsis', () => {
    render(<BecauseSection reasons={reasons} />);
    expect(screen.getByText('...')).toBeInTheDocument();
  });

  it('expands to show all reasons when clicked', async () => {
    const user = userEvent.setup();
    render(<BecauseSection reasons={reasons} />);

    const expandButton = screen.getByRole('button');
    await user.click(expandButton);

    expect(screen.getByText('method matched')).toBeInTheDocument();
    expect(screen.getByText("path didn't match")).toBeInTheDocument();
    expect(screen.getByText('some neutral info')).toBeInTheDocument();
  });

  it('collapses again when clicked a second time', async () => {
    const user = userEvent.setup();
    render(<BecauseSection reasons={reasons} />);

    const expandButton = screen.getByRole('button');
    await user.click(expandButton);
    expect(screen.getByText('method matched')).toBeInTheDocument();

    await user.click(expandButton);
    expect(screen.getByText('...')).toBeInTheDocument();
  });

  it('applies green color to matched reasons', async () => {
    const user = userEvent.setup();
    render(<BecauseSection reasons={['method matched']} />);

    await user.click(screen.getByRole('button'));

    const reason = screen.getByText('method matched');
    expect(reason).toHaveStyle({ color: 'rgb(107, 199, 118)' });
  });

  it('applies red color to didnt-match reasons', async () => {
    const user = userEvent.setup();
    render(<BecauseSection reasons={["path didn't match"]} />);

    await user.click(screen.getByRole('button'));

    const reason = screen.getByText("path didn't match");
    expect(reason).toHaveStyle({ color: 'rgb(216, 88, 118)' });
  });

  it('applies white color to neutral reasons', async () => {
    const user = userEvent.setup();
    render(<BecauseSection reasons={['some info']} />);

    await user.click(screen.getByRole('button'));

    const reason = screen.getByText('some info');
    expect(reason).toHaveStyle({ color: 'rgb(255, 255, 255)' });
  });
});
