# frozen_string_literal: true

require 'json'

RSpec.describe 'retrieve recorded expectations with RecordedExpectationsOptions' do
  let(:base_url) { 'http://localhost:1080' }
  let(:client) { MockServer::Client.new('localhost', 1080) }
  let(:sent_paths) { [] }
  let(:recorded) { 'type=RECORDED_EXPECTATIONS&format=JSON' }

  after { client.close }

  # Records the exact request-target Net::HTTP puts on the wire.
  def capture_sent_paths
    allow(client).to receive(:build_request).and_wrap_original do |original, *args|
      original.call(*args).tap { |req| sent_paths << req.path }
    end
  end

  def sent_query
    expect(sent_paths.length).to eq(1)
    sent_paths.first.split('?', 2)[1]
  end

  def options
    MockServer::RecordedExpectationsOptions.new
  end

  before do
    capture_sent_paths
    stub_request(:put, %r{\A#{Regexp.escape(base_url)}/mockserver/retrieve})
      .to_return(status: 200, body: JSON.generate([{
                                                    'httpRequest' => { 'method' => 'GET', 'path' => '/jobs/1' },
                                                    'httpResponses' => [
                                                      { 'statusCode' => 200, 'body' => 'pending' },
                                                      { 'statusCode' => 200, 'body' => 'done' }
                                                    ],
                                                    'responseMode' => 'SWITCH',
                                                    'times' => { 'unlimited' => true }
                                                  }]))
  end

  it 'sends every option with a request matcher' do
    client.retrieve_recorded_expectations(
      request: MockServer::HttpRequest.new(path: '/jobs/.*'),
      options: options.with_consolidate.with_parameterize.with_repeats_as_scenarios
                      .with_capture_headers('X-Tenant', 'Accept').with_extract_bodies_larger_than(1024)
    )

    expect(sent_query).to eq("#{recorded}&consolidate=true&parameterize=true&repeatsAsScenarios=true" \
                             '&captureHeaders=X-Tenant%2CAccept&extractBodiesLargerThan=1024')
    expect(WebMock).to(have_requested(:put, %r{/mockserver/retrieve}).with { |req| JSON.parse(req.body) == { 'path' => '/jobs/.*' } })
  end

  it 'sends an empty captureHeaders, which keeps no headers' do
    client.retrieve_recorded_expectations(options: options.with_capture_headers)

    expect(sent_query).to eq("#{recorded}&captureHeaders=")
  end

  it 'sends a threshold of zero' do
    client.retrieve_recorded_expectations(options: options.with_extract_bodies_larger_than(0))

    expect(sent_query).to eq("#{recorded}&extractBodiesLargerThan=0")
  end

  it 'sends nothing extra for false or unset options' do
    client.retrieve_recorded_expectations(options: options.with_repeats_as_scenarios(false))

    expect(sent_query).to eq(recorded)
  end

  it 'combines with paging' do
    client.retrieve_recorded_expectations(offset: 5, limit: 10, options: options.with_repeats_as_scenarios)

    expect(sent_query).to eq("#{recorded}&repeatsAsScenarios=true&offset=5&limit=10")
  end

  it 'returns the sequenced expectation' do
    result = client.retrieve_recorded_expectations(options: options.with_repeats_as_scenarios)

    expect(result.length).to eq(1)
    expect(result[0].response_mode).to eq('SWITCH')
    expect(result[0].http_responses.map(&:body)).to eq(%w[pending done])
  end

  describe 'rejects invalid options before sending' do
    {
      'a non-boolean repeats_as_scenarios' => -> { MockServer::RecordedExpectationsOptions.new.with_repeats_as_scenarios('true') },
      'a non-boolean consolidate' => -> { MockServer::RecordedExpectationsOptions.new.with_consolidate(nil) },
      'a blank header name' => -> { MockServer::RecordedExpectationsOptions.new.with_capture_headers('X-Tenant', ' ') },
      'a header name with a comma' => -> { MockServer::RecordedExpectationsOptions.new.with_capture_headers('X-Tenant,Accept') },
      'a non-String header name' => -> { MockServer::RecordedExpectationsOptions.new.with_capture_headers(%w[X-Tenant]) },
      'a negative threshold' => -> { MockServer::RecordedExpectationsOptions.new.with_extract_bodies_larger_than(-1) },
      'a String threshold' => -> { MockServer::RecordedExpectationsOptions.new.with_extract_bodies_larger_than('1024') }
    }.each do |name, build|
      it name do
        expect { build.call }.to raise_error(ArgumentError)
      end
    end

    it 'an options Hash' do
      expect { client.retrieve_recorded_expectations(options: { 'repeatsAsScenarios' => true }) }.to raise_error(ArgumentError)
      expect(sent_paths).to be_empty
    end
  end
end
