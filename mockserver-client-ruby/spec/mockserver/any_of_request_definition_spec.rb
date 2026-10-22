# frozen_string_literal: true

require 'json'

RSpec.describe MockServer::AnyOfRequestDefinition do
  def wire(model)
    JSON.parse(JSON.generate(model.to_h))
  end

  let(:matcher) { { 'anyOf' => [{ 'method' => 'GET', 'path' => '/pets' }, { 'method' => 'GET', 'path' => '/animals' }] } }

  it 'reads an expectation matcher holding anyOf as an AnyOfRequestDefinition' do
    expectation = MockServer::Expectation.from_hash('httpRequest' => matcher, 'httpResponse' => { 'statusCode' => 200 })
    any_of = expectation.http_request
    expect(any_of).to be_a(described_class)
    expect(any_of.any_of.map(&:class)).to eq([MockServer::HttpRequest] * 2)
    expect(any_of.any_of.map(&:path)).to eq(%w[/pets /animals])
    expect(wire(expectation)).to eq('httpRequest' => matcher, 'httpResponse' => { 'statusCode' => 200 })
  end

  it 'writes typed branches as an anyOf array inside httpRequest' do
    expectation = MockServer::Expectation.new(
      http_request: described_class.new(any_of: [
        MockServer::HttpRequest.new(method: 'GET', path: '/pets'),
        MockServer::HttpRequest.new(method: 'GET', path: '/animals')
      ]),
      http_response: MockServer::HttpResponse.new(status_code: 200)
    )
    expect(wire(expectation)).to eq('httpRequest' => matcher, 'httpResponse' => { 'statusCode' => 200 })
  end

  it 'reads every branch kind and not' do
    nested = {
      'not' => true,
      'anyOf' => [
        { 'path' => '/a' },
        { 'specUrlOrPayload' => 'https://example.com/o.json', 'operationId' => 'listPets' },
        { 'if' => { 'method' => 'POST' }, 'then' => { 'path' => '/orders' } },
        { 'not' => true, 'anyOf' => [{ 'path' => '/admin/.*' }] }
      ]
    }
    model = described_class.from_hash(nested)
    expect(model.not_any_of).to eq(true)
    expect(model.any_of.map(&:class)).to eq([MockServer::HttpRequest, MockServer::OpenAPIDefinition,
                                             MockServer::ConditionalRequestDefinition, described_class])
    expect(wire(model)).to eq(nested)
  end

  it 'is read inside a conditional branch' do
    conditional = { 'if' => { 'method' => 'GET' }, 'then' => matcher }
    model = MockServer::ConditionalRequestDefinition.from_hash(conditional)
    expect(model.then_request).to be_a(described_class)
    expect(wire(model)).to eq(conditional)
  end

  it 'reads verification and verifySequence matchers the same way' do
    verification = { 'httpRequest' => matcher, 'times' => { 'atLeast' => 1 } }
    expect(MockServer::Verification.from_hash(verification).http_request).to be_a(described_class)
    expect(wire(MockServer::Verification.from_hash(verification))).to eq(verification)
    sequence = { 'httpRequests' => [matcher, { 'path' => '/b' }] }
    model = MockServer::VerificationSequence.from_hash(sequence)
    expect(model.http_requests.map(&:class)).to eq([described_class, MockServer::HttpRequest])
    expect(wire(model)).to eq(sequence)
  end

  it 'sends an empty anyOf for the server to reject rather than dropping it to a match-all {}' do
    expect(wire(described_class.new)).to eq('anyOf' => [])
    expect(wire(described_class.new(any_of: nil))).to eq('anyOf' => [])
    expect(wire(MockServer::Expectation.new(http_request: described_class.new))['httpRequest']).to eq('anyOf' => [])
    expect(wire(described_class.from_hash('anyOf' => []))).to eq('anyOf' => [])
  end

  it 'reads a symbol-key Hash' do
    expectation = MockServer::Expectation.from_hash(httpRequest: { anyOf: [{ path: '/a' }, { path: '/b' }] })
    expect(expectation.http_request).to be_a(described_class)
    expect(wire(expectation)).to eq('httpRequest' => { 'anyOf' => [{ 'path' => '/a' }, { 'path' => '/b' }] })
  end

  it 'reads which branch an explanation found closest' do
    explained = MockServer::ExplainedExpectation.from_hash(
      'expectationId' => 'any-of',
      'anyOf' => { 'closestBranch' => 1,
                   'branches' => [{ 'branch' => 0, 'differingFieldCount' => 2 }, { 'branch' => 1, 'differingFieldCount' => 1 }] }
    )
    expect(explained.any_of).to be_a(MockServer::AnyOfBranchComparison)
    expect(explained.any_of.closest_branch).to eq(1)
    expect(explained.any_of.branches.length).to eq(2)
    expect(MockServer::ExplainedExpectation.from_hash('expectationId' => 'plain').any_of).to be_nil
  end
end
