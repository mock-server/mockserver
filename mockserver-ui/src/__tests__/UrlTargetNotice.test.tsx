import { describe, it, expect, afterEach } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import UrlTargetNotice from '../components/UrlTargetNotice';

function at(search: string): void {
  Object.defineProperty(window, 'location', {
    writable: true,
    configurable: true,
    value: {
      protocol: 'http:', hostname: 'localhost', port: '1080', search, pathname: '/mockserver/dashboard/', hash: '#/dashboard',
      href: `http://localhost:1080/mockserver/dashboard/${search}#/dashboard`,
    },
  });
}

describe('UrlTargetNotice', () => {
  afterEach(() => at(''));

  it('shows nothing when the link names a valid target on the page\'s host, or none', () => {
    at('?host=localhost&port=1081&basePath=/mock');
    render(<UrlTargetNotice />);
    expect(screen.queryByTestId('url-target-notice')).toBeNull();
    expect(screen.queryByTestId('url-target-foreign-host')).toBeNull();
  });

  it('names a server on another host the link points at, with a link back to the page\'s own server', () => {
    at('?host=attacker.example&port=443&secure=true');
    render(<UrlTargetNotice />);
    const notice = screen.getByTestId('url-target-foreign-host');
    expect(notice).toHaveTextContent(
      'This dashboard link points at attacker.example:443, not the server that served this page. ' +
        'Workspaces that follow the link show that server\'s data, and what you create or upload in them is sent there.',
    );
    expect(screen.getByRole('link', { name: 'Use localhost:1080' })).toHaveAttribute(
      'href',
      'http://localhost:1080/mockserver/dashboard/#/dashboard',
    );
    expect(screen.queryByRole('button', { name: /close/i })).toBeNull();
    expect(screen.queryByTestId('url-target-notice')).toBeNull();
  });

  it('names the ignored value and the server used instead', () => {
    at('?basePath=/mockserver/reset%23');
    render(<UrlTargetNotice />);
    expect(screen.getByTestId('url-target-notice')).toHaveTextContent(
      'The base path in this dashboard link is not valid, so the dashboard ignored it; workspaces that follow the link use localhost:1080.',
    );
  });

  it('lists every ignored value, and can be dismissed', () => {
    at('?host=x@y&port=99999&basePath=/a%3F');
    render(<UrlTargetNotice />);
    expect(screen.getByTestId('url-target-notice')).toHaveTextContent(
      'The host, port and base path in this dashboard link are not valid, so the dashboard ignored them; workspaces that follow the link use localhost:1080.',
    );
    fireEvent.click(screen.getByRole('button', { name: /close/i }));
    expect(screen.queryByTestId('url-target-notice')).toBeNull();
  });
});
