/**
 * Behaviour of the filter-DSL "quick scope" box on the verification request matcher.
 *
 * Two things matter here and are asserted as observable behaviour:
 *  1. an operator the verification matcher cannot honour is visibly refused, never
 *     silently dropped (that is the whole reason the vocabulary is restricted);
 *  2. the existing fields keep working — the scope box only ever fills them, so no
 *     capability (raw regex paths, multi-line headers, query, body) is lost.
 */
import { describe, it, expect, afterEach, vi } from 'vitest';
import { render, screen, cleanup } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ThemeProvider } from '@mui/material/styles';
import { buildTheme } from '../theme';
import VerificationView from '../components/VerificationView';

const params = { host: '127.0.0.1', port: '1080', secure: false };

function renderView() {
  return render(
    <ThemeProvider theme={buildTheme('dark')}>
      <VerificationView connectionParams={params} />
    </ThemeProvider>,
  );
}

const scopeInput = () => screen.getAllByLabelText('Quick scope')[0]!;
const applyButton = () => screen.getAllByRole('button', { name: 'Apply scope' })[0]!;

afterEach(cleanup);

describe('VerificationView quick scope', () => {
  it('advertises only the operators the request and response matchers can honour', () => {
    renderView();
    const placeholder = scopeInput().getAttribute('placeholder') ?? '';
    expect(placeholder).toContain('method:POST');
    expect(placeholder).toContain('path:/api/*');
    expect(placeholder).toContain('host:*.example.com');
    expect(placeholder).toContain('status:>=400');
    expect(placeholder).not.toContain('operation:');
  });

  it('fills the method and path fields, compiling the path glob to a regex', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'method:post path:/api/*');
    await user.click(applyButton());

    // MockServer full-matches a regex path, so the glob becomes /api/.* — and it is
    // shown in the Path field, so what will be sent stays visible and editable.
    expect(screen.getByLabelText('Path')).toHaveValue('/api/.*');
    expect(screen.getByText('POST')).toBeInTheDocument();
    // The shorthand is consumed once.
    expect(scopeInput()).toHaveValue('');
  });

  it('expresses host as a Host header matcher, since httpRequest has no host field', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'host:*.example.com');
    await user.click(applyButton());

    expect(screen.getByLabelText('Headers (Name: value per line)'))
      .toHaveValue('Host: .*\\.example\\.com');
  });

  it('refuses an operator it cannot honour, explaining what is supported', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'operation:GetUser');

    const explanation = screen.getByText(/not supported here/i);
    expect(explanation).toHaveTextContent('operation:');
    expect(explanation).toHaveTextContent('Supported here: method:, path:, host:, status:');
    expect(scopeInput()).toHaveAttribute('aria-invalid', 'true');
    expect(applyButton()).toBeDisabled();
    // Nothing was applied behind the scenes.
    expect(screen.getByLabelText('Path')).toHaveValue('');
  });

  it('refuses free text rather than silently dropping it', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'method:GET orders');

    expect(screen.getByText(/is not a scope operator/i)).toBeInTheDocument();
    expect(applyButton()).toBeDisabled();
    // The valid half of the term is not applied either — a refused term does nothing.
    expect(screen.getByLabelText('Path')).toHaveValue('');
  });

  it('refuses a method the matcher form cannot represent', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'method:TRACE');

    expect(screen.getByText(/method:TRACE is not one of/)).toBeInTheDocument();
    expect(applyButton()).toBeDisabled();
  });

  it('keeps Apply disabled until the term contributes something', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    expect(applyButton()).toBeDisabled();
    await user.type(scopeInput(), 'method:GET');
    expect(applyButton()).toBeEnabled();
  });

  it('does not take over the existing fields — a raw regex path still works', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    // A regex the glob DSL cannot express, typed straight into the field.
    await user.type(screen.getByLabelText('Path'), '/api/(orders|carts)');
    await user.type(screen.getByLabelText('Body (substring/JSON match)'), 'order-id-1');

    // Applying a scope leaves untouched fields alone and only overwrites what it sets.
    await user.type(scopeInput(), 'method:GET');
    await user.click(applyButton());

    expect(screen.getByLabelText('Path')).toHaveValue('/api/(orders|carts)');
    expect(screen.getByLabelText('Body (substring/JSON match)')).toHaveValue('order-id-1');
    expect(screen.getByText('GET')).toBeInTheDocument();
  });

  it('gives every ordered-sequence step its own scope box', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.click(screen.getByRole('button', { name: 'Ordered sequence' }));
    // Each step's box is named for its step, so a screen reader can tell them apart.
    expect(screen.queryByLabelText('Quick scope')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Scope for step 1')).toBeInTheDocument();

    await user.type(screen.getByLabelText('Scope for step 2'), 'path:/step/*');
    await user.click(screen.getAllByRole('button', { name: 'Apply scope' })[1]!);

    const paths = screen.getAllByLabelText('Path');
    expect(paths[0]).toHaveValue('');
    expect(paths[1]).toHaveValue('/step/.*');
  });

  it('puts a status comparison into the response status range and opens the response matcher', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'method:GET status:>=400');
    await user.click(applyButton());

    expect(screen.getByLabelText('Status range')).toHaveValue('>= 400');
    expect(screen.getByLabelText('Status code')).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Collapse Response matcher (optional)' })).toBeInTheDocument();
  });

  it('puts an exact status into the status code, and a whole class into the range', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'status:404');
    await user.click(applyButton());
    expect(screen.getByLabelText('Status code')).toHaveValue('404');
    expect(screen.getByLabelText('Status range')).toHaveValue('');

    await user.type(scopeInput(), 'status:>=500 status:<600');
    await user.click(applyButton());
    expect(screen.getByLabelText('Status code')).toHaveValue('');
    expect(screen.getByLabelText('Status range')).toHaveValue('5XX');
  });

  it('refuses a status range that needs two comparisons', async () => {
    const user = userEvent.setup({ delay: null });
    renderView();

    await user.type(scopeInput(), 'status:>=400 status:<450');

    expect(screen.getByText(/selects 400-449, which needs two comparisons/)).toBeInTheDocument();
    expect(applyButton()).toBeDisabled();
  });

  it('sends the range, and blocks Verify for a range the server could not read or a code and range together', async () => {
    const fetchMock = vi.fn(async () => new Response('', { status: 202 }));
    vi.stubGlobal('fetch', fetchMock);
    const user = userEvent.setup({ delay: null });
    renderView();
    await user.click(screen.getByRole('button', { name: 'Expand Response matcher (optional)' }));

    const range = screen.getByLabelText('Status range');
    await user.type(range, 'between 4 and 5');
    expect(screen.getByText(/Use a class such as 4XX/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled();

    await user.clear(range);
    await user.type(range, '4xx');
    await user.type(screen.getByLabelText('Status code'), '404');
    expect(screen.getByText('Fill the status code or the status range, not both.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Verify' })).toBeDisabled();

    await user.clear(screen.getByLabelText('Status code'));
    await user.click(screen.getByRole('button', { name: 'Verify' }));
    const body = JSON.parse((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].body as string) as Record<string, unknown>;
    expect(body['httpResponse']).toEqual({ statusCodeRange: '4xx' });
    vi.unstubAllGlobals();
  });
});
