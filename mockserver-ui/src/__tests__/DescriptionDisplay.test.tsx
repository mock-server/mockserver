import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import DescriptionDisplay from '../components/DescriptionDisplay';

describe('DescriptionDisplay', () => {
  it('renders a plain string description', () => {
    render(<DescriptionDisplay description="GET /api/users" />);
    expect(screen.getByText('GET /api/users')).toBeInTheDocument();
  });

  it('renders a non-json structured description with first and second parts', () => {
    render(
      <DescriptionDisplay description={{ json: false, first: 'GET', second: '/api/orders' }} />,
    );
    expect(screen.getByText('GET')).toBeInTheDocument();
    expect(screen.getByText('/api/orders')).toBeInTheDocument();
  });

  it('renders a json structured description with first, object, and second parts', () => {
    render(
      <DescriptionDisplay
        description={{
          json: true,
          first: 'spec ',
          object: { openapi: '3.0.0' },
          second: ' operationId',
        }}
      />,
    );
    expect(screen.getByText('spec')).toBeInTheDocument();
    expect(screen.getByText('operationId')).toBeInTheDocument();
  });
});
