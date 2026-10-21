import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import DateTimeMatcherDialog from '../components/DateTimeMatcherDialog';

function renderDialog() {
  const props = { open: true, onClose: vi.fn(), onAdd: vi.fn() };
  render(
    <ThemeProvider theme={buildTheme('dark')}>
      <DateTimeMatcherDialog {...props} />
    </ThemeProvider>,
  );
  return props;
}

describe('DateTimeMatcherDialog', () => {
  it('adds a header line holding only the options that are set', async () => {
    const user = userEvent.setup();
    const props = renderDialog();
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'X-Issued-At');
    await user.type(screen.getByRole('textbox', { name: 'Expected Value' }), 'now -1 hours');
    await user.type(screen.getByRole('textbox', { name: 'Zone (optional)' }), 'Europe/London');
    await user.click(screen.getByRole('checkbox', { name: 'Not (Negate the Match)' }));
    await user.click(screen.getByRole('button', { name: 'Add' }));
    expect(props.onAdd).toHaveBeenCalledWith(
      'headers',
      'X-Issued-At: {"not":true,"after":"now -1 hours","zone":"Europe/London"}',
    );
    expect(props.onClose).toHaveBeenCalledOnce();
  });

  it('adds a cookie line with the chosen comparison', async () => {
    const user = userEvent.setup();
    const props = renderDialog();
    await user.click(screen.getByRole('combobox', { name: 'Field' }));
    await user.click(screen.getByRole('option', { name: 'Cookie' }));
    await user.click(screen.getByRole('combobox', { name: 'Comparison' }));
    await user.click(screen.getByRole('option', { name: 'Equal To Date-Time' }));
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'seen');
    await user.type(screen.getByRole('textbox', { name: 'Expected Value' }), 'now');
    await user.click(screen.getByRole('checkbox', { name: 'Apply Truncation Last' }));
    await user.click(screen.getByRole('button', { name: 'Add' }));
    expect(props.onAdd).toHaveBeenCalledWith('cookies', 'seen={"equalToDateTime":"now","applyTruncationLast":true}');
  });

  it('needs a name and an expected value, and rejects a name holding the separator', async () => {
    const user = userEvent.setup();
    const props = renderDialog();
    const add = screen.getByRole('button', { name: 'Add' });
    expect(add).toBeDisabled();
    await user.type(screen.getByRole('textbox', { name: 'Expected Value' }), 'now');
    expect(add).toBeDisabled();
    await user.type(screen.getByRole('textbox', { name: 'Name' }), 'Bad:Name');
    expect(screen.getByText('Name cannot contain ":"')).toBeInTheDocument();
    expect(add).toBeDisabled();
    expect(props.onAdd).not.toHaveBeenCalled();
  });
});
