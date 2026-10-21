# frozen_string_literal: true

require 'json'

RSpec.describe 'expectation metadata, filtered retrieve / clear and paging' do
  let(:base_url) { 'http://localhost:1080' }
  let(:client) { MockServer::Client.new('localhost', 1080) }
  let(:sent_paths) { [] }
  let(:fixture_path) do
    File.expand_path('../../../test-fixtures/expectations/top_metadata.json', __dir__)
  end

  after { client.close }

  # Records the exact request-target Net::HTTP puts on the wire.
  def capture_sent_paths
    allow(client).to receive(:build_request).and_wrap_original do |original, *args|
      original.call(*args).tap { |req| sent_paths << req.path }
    end
  end

  def stub_control_plane(body = '[]')
    stub_request(:put, %r{\A#{Regexp.escape(base_url)}/mockserver/(retrieve|clear)})
      .to_return(status: 200, body: body)
  end

  def sent_query
    expect(sent_paths.length).to eq(1)
    sent_paths.first.split('?', 2)[1]
  end

  describe 'Expectation#metadata' do
    it 'round trips the shared fixture exactly, with String keys and nil values kept' do
      raw = JSON.parse(File.read(fixture_path))
      expectation = MockServer::Expectation.from_hash(raw)

      expect(expectation.metadata.keys).to all(be_a(String))
      expect(expectation.metadata).to have_key('note')
      expect(expectation.metadata['note']).to be_nil
      expect(expectation.metadata['contact']).to eq({ 'team' => 'checkout', 'oncall' => %w[ann bob] })
      expect(expectation.metadata['ticket']).to eq(4211)
      expect(expectation.metadata['ratio']).to eq(0.5)
      expect(expectation.metadata['flaky']).to be(false)
      expect(JSON.parse(JSON.generate(expectation.to_h))).to eq(raw)
    end

    it 'converts Symbol keys to Strings at any depth' do
      expectation = MockServer::Expectation.new(metadata: { owner: 'team-a', contact: { team: 'x' }, gone: nil })

      expect(expectation.metadata).to eq({ 'owner' => 'team-a', 'contact' => { 'team' => 'x' }, 'gone' => nil })
      expect(expectation.to_h['metadata']).to eq({ 'owner' => 'team-a', 'contact' => { 'team' => 'x' }, 'gone' => nil })
    end

    it 'reads a symbol-keyed hash with String metadata keys' do
      expectation = MockServer::Expectation.from_hash(JSON.parse(File.read(fixture_path), symbolize_names: true))

      expect(expectation.metadata.keys).to all(be_a(String))
      expect(expectation.metadata['note']).to be_nil
      expect(expectation.metadata).to have_key('note')
    end

    it 'omits empty or absent metadata' do
      expect(MockServer::Expectation.new.to_h).not_to have_key('metadata')
      expect(MockServer::Expectation.new(metadata: {}).to_h).not_to have_key('metadata')
    end

    it 'rejects metadata that is not a Hash' do
      expect { MockServer::Expectation.new(metadata: 'owner=team-a') }.to raise_error(ArgumentError)
      expect { MockServer::Expectation.new.metadata = %w[a b] }.to raise_error(ArgumentError)
    end

    it 'is sent by the fluent builder' do
      stub_request(:put, "#{base_url}/mockserver/expectation").to_return(status: 201, body: '[]')

      client.when(MockServer::HttpRequest.new(path: '/m'))
            .with_metadata(owner: 'team-a', ticket: 42)
            .respond(MockServer::HttpResponse.new(status_code: 200))

      expect(WebMock).to(have_requested(:put, "#{base_url}/mockserver/expectation").with do |req|
        JSON.parse(req.body)[0]['metadata'] == { 'owner' => 'team-a', 'ticket' => 42 }
      end)
    end
  end

  describe 'retrieve' do
    before do
      capture_sent_paths
      stub_control_plane(JSON.generate([JSON.parse(File.read(fixture_path))]))
    end

    {
      '!team-a' => 'metadataValue=%21team-a',
      '?x' => 'metadataValue=%3Fx',
      '100%' => 'metadataValue=100%25',
      'a+b' => 'metadataValue=a%2Bb',
      'a b' => 'metadataValue=a+b',
      'a&b=c#d' => 'metadataValue=a%26b%3Dc%23d'
    }.each do |value, encoded|
      it "sends the value #{value.inspect} literally as #{encoded}" do
        client.retrieve_active_expectations_by_metadata('owner', value)

        expect(sent_query).to eq("type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner&#{encoded}")
      end
    end

    it 'sends a key with reserved characters literally' do
      client.retrieve_active_expectations_by_metadata_key('!team key')

      expect(sent_query).to eq('type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=%21team+key')
    end

    it 'returns typed expectations with their metadata' do
      result = client.retrieve_active_expectations_by_metadata('owner', 'team-a')

      expect(result.length).to eq(1)
      expect(result[0]).to be_a(MockServer::Expectation)
      expect(result[0].metadata['owner']).to eq('team-a')
    end

    {
      42 => '42',
      true => 'true',
      0.5 => '0.5',
      %w[a b] => '%5B%22a%22%2C%22b%22%5D',
      { team: 'x' } => '%7B%22team%22%3A%22x%22%7D',
      MockServer::ExpectationFilter::JSON_NULL => 'null'
    }.each do |value, encoded|
      it "sends the non-String value #{value.inspect} as compact JSON" do
        client.retrieve_active_expectations_by_metadata('k', value)

        expect(sent_query).to eq("type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=k&metadataValue=#{encoded}")
      end
    end

    it 'sends a key-only selection without a value' do
      client.retrieve_active_expectations_by_metadata_key('owner')

      expect(sent_query).to eq('type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner')
    end

    it 'drops an earlier value when the filter switches to key-only' do
      filter = MockServer::ExpectationFilter.new.with_metadata('owner', 'team-a').with_metadata_key('owner')
      client.retrieve_active_expectations(filter: filter)

      expect(sent_query).to eq('type=ACTIVE_EXPECTATIONS&format=JSON&metadataKey=owner')
    end

    it 'sends unmatched=true for retrieve_unmatched_expectations' do
      client.retrieve_unmatched_expectations

      expect(sent_query).to eq('type=ACTIVE_EXPECTATIONS&format=JSON&unmatched=true')
    end

    it 'sends JSONPath, unmatched, offset and limit with the request matcher body' do
      filter = MockServer::ExpectationFilter.new
                                            .with_metadata_json_path("$[?(@.owner == 'team-a')]")
                                            .with_unmatched
                                            .with_offset(5)
                                            .with_limit(0)
      client.retrieve_active_expectations(request: MockServer::HttpRequest.new(path: '/p'), filter: filter)

      expect(sent_query).to eq(
        'type=ACTIVE_EXPECTATIONS&format=JSON&metadataJsonPath=%24%5B%3F%28%40.owner+%3D%3D+%27team-a%27%29%5D' \
        '&unmatched=true&offset=5&limit=0'
      )
      expect(WebMock).to(have_requested(:put, %r{/mockserver/retrieve}).with { |req| JSON.parse(req.body) == { 'path' => '/p' } })
    end

    it 'keeps the existing query when no filter is given' do
      client.retrieve_active_expectations

      expect(sent_query).to eq('type=ACTIVE_EXPECTATIONS&format=JSON')
    end

    it 'pages recorded expectations with offset and limit only' do
      client.retrieve_recorded_expectations(offset: 10, limit: 20)

      expect(sent_query).to eq('type=RECORDED_EXPECTATIONS&format=JSON&offset=10&limit=20')
    end

    it 'keeps the existing recorded query without paging' do
      client.retrieve_recorded_expectations

      expect(sent_query).to eq('type=RECORDED_EXPECTATIONS&format=JSON')
    end
  end

  describe 'clear' do
    before do
      capture_sent_paths
      stub_control_plane('')
    end

    it 'clears by metadata key and value with type=expectations' do
      expect(client.clear_by_metadata('owner', '!team-a')).to be_nil

      expect(sent_query).to eq('type=expectations&metadataKey=owner&metadataValue=%21team-a')
    end

    it 'clears by metadata key only' do
      client.clear_by_metadata_key('owner')

      expect(sent_query).to eq('type=expectations&metadataKey=owner')
    end

    it 'clears unmatched expectations' do
      client.clear_unmatched_expectations

      expect(sent_query).to eq('type=expectations&unmatched=true')
    end

    it 'clears with a filter and request matcher' do
      filter = MockServer::ExpectationFilter.new.with_metadata_json_path('$.tags[?(@ == "smoke")]')
      client.clear(MockServer::HttpRequest.new(path: '/p'), filter: filter)

      expect(sent_query).to eq('type=expectations&metadataJsonPath=%24.tags%5B%3F%28%40+%3D%3D+%22smoke%22%29%5D')
      expect(WebMock).to(have_requested(:put, %r{/mockserver/clear}).with { |req| JSON.parse(req.body) == { 'path' => '/p' } })
    end

    it 'keeps the existing clear query when no filter is given' do
      client.clear(type: 'LOG')

      expect(sent_query).to eq('type=LOG')
    end

    it 'raises Error when the server rejects the filter' do
      stub_request(:put, %r{/mockserver/clear}).to_return(status: 400, body: 'bad filter')

      expect { client.clear_unmatched_expectations }.to raise_error(MockServer::Error, /bad filter/)
    end
  end

  describe 'fail-closed rejections send nothing' do
    before { capture_sent_paths }

    after do
      expect(sent_paths).to be_empty
      expect(WebMock).not_to have_requested(:any, /.*/)
    end

    [nil, '', '   ', 42].each do |key|
      it "rejects the metadata key #{key.inspect}" do
        expect { client.clear_by_metadata(key, 'v') }.to raise_error(ArgumentError)
        expect { client.clear_by_metadata_key(key) }.to raise_error(ArgumentError)
        expect { client.retrieve_active_expectations_by_metadata(key, 'v') }.to raise_error(ArgumentError)
        expect { client.retrieve_active_expectations_by_metadata_key(key) }.to raise_error(ArgumentError)
      end
    end

    it 'rejects a nil value in the key+value form' do
      expect { client.clear_by_metadata('owner', nil) }.to raise_error(ArgumentError, /with_metadata_key/)
      expect { client.retrieve_active_expectations_by_metadata('owner', nil) }.to raise_error(ArgumentError)
    end

    [nil, '', '  '].each do |path|
      it "rejects the JSONPath #{path.inspect}" do
        expect { MockServer::ExpectationFilter.new.with_metadata_json_path(path) }.to raise_error(ArgumentError)
      end
    end

    it 'rejects a clear with no selection' do
      expect { client.clear(filter: MockServer::ExpectationFilter.new) }.to raise_error(ArgumentError, /selection/)
      expect { client.clear(filter: MockServer::ExpectationFilter.new.with_unmatched(false)) }.to raise_error(ArgumentError)
    end

    it 'rejects an explicit nil or non-filter clear filter' do
      expect { client.clear(filter: nil) }.to raise_error(ArgumentError)
      expect { client.clear(filter: { 'metadataKey' => 'owner' }) }.to raise_error(ArgumentError)
    end

    it 'rejects paging on clear' do
      filter = MockServer::ExpectationFilter.new.with_metadata_key('owner').with_limit(1)
      expect { client.clear(filter: filter) }.to raise_error(ArgumentError, /not supported for clear/)
      filter = MockServer::ExpectationFilter.new.with_unmatched.with_offset(0)
      expect { client.clear(filter: filter) }.to raise_error(ArgumentError, /not supported for clear/)
    end

    it 'rejects a clear filter combined with a non-expectations type' do
      filter = MockServer::ExpectationFilter.new.with_unmatched
      expect { client.clear(type: 'ALL', filter: filter) }.to raise_error(ArgumentError, /only clears expectations/)
    end

    [-1, 1.5, '3', nil].each do |bad|
      it "rejects the offset / limit #{bad.inspect}" do
        expect { MockServer::ExpectationFilter.new.with_offset(bad) }.to raise_error(ArgumentError)
        expect { MockServer::ExpectationFilter.new.with_limit(bad) }.to raise_error(ArgumentError)
      end
    end

    it 'rejects negative recorded paging before sending' do
      expect { client.retrieve_recorded_expectations(offset: -1) }.to raise_error(ArgumentError)
      expect { client.retrieve_recorded_expectations(limit: -5) }.to raise_error(ArgumentError)
    end

    it 'rejects a retrieve filter that is not an ExpectationFilter' do
      expect { client.retrieve_active_expectations(filter: { 'unmatched' => true }) }.to raise_error(ArgumentError)
    end

    it 'rejects a non-boolean unmatched flag' do
      expect { MockServer::ExpectationFilter.new.with_unmatched('true') }.to raise_error(ArgumentError)
    end

    it 'rejects a value that cannot be sent as JSON' do
      expect { client.clear_by_metadata('ratio', Float::NAN) }.to raise_error(ArgumentError)
    end
  end
end
