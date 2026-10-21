# frozen_string_literal: true

require 'json'

RSpec.describe MockServer::DateTimeMatcher do
  def wire(model)
    JSON.parse(JSON.generate(model.to_h))
  end

  describe 'builders' do
    it 'builds each operator' do
      expect(described_class.before('2026-10-21T00:00:00Z').to_h).to eq({ 'before' => '2026-10-21T00:00:00Z' })
      expect(described_class.after('now -1 hours').to_h).to eq({ 'after' => 'now -1 hours' })
      expect(described_class.equal_to_date_time('now').to_h).to eq({ 'equalToDateTime' => 'now' })
    end

    it 'writes every option that is set, in the server order' do
      matcher = described_class.equal_to_date_time('now +1 days')
                               .with_not(true)
                               .with_actual_format('rfc1123')
                               .with_truncate_expected('first day of month')
                               .with_truncate_actual('startOfDay')
                               .with_apply_truncation_last(false)
                               .with_zone('Europe/London')

      expect(JSON.parse(matcher.to_json)).to eq(matcher.to_h)
      expect(JSON.generate(matcher.to_h)).to eq(
        '{"not":true,"equalToDateTime":"now +1 days","actualFormat":"rfc1123",' \
        '"truncateExpected":"first day of month","truncateActual":"startOfDay",' \
        '"applyTruncationLast":false,"zone":"Europe/London"}'
      )
    end

    it 'omits not when false and never writes null' do
      expect(described_class.after('now').with_not(false).to_h).to eq({ 'after' => 'now' })
      expect(described_class.after('now').with_zone(nil).to_h).to eq({ 'after' => 'now' })
    end

    it 'is immutable: each option returns a new matcher' do
      base = described_class.after('now')
      zoned = base.with_zone('UTC')

      expect(base).to be_frozen
      expect(base.to_h).to eq({ 'after' => 'now' })
      expect(zoned.to_h).to eq({ 'after' => 'now', 'zone' => 'UTC' })
      expect(zoned).not_to equal(base)
    end

    it 'rejects an unknown operator' do
      expect { described_class.new(operator: 'since', expected: 'now') }.to raise_error(ArgumentError)
    end

    it 'rejects two operators when read on its own' do
      expect { described_class.from_hash({ 'before' => 'now', 'after' => 'now' }) }.to raise_error(ArgumentError)
    end
  end

  describe 'in request values' do
    let(:matcher) { described_class.after('now -1 hours').with_actual_format('rfc1123') }
    let(:object) { { 'after' => 'now -1 hours', 'actualFormat' => 'rfc1123' } }

    it 'serialises in headers, query parameters, path parameters and cookies' do
      request = MockServer::HttpRequest.new(path: '/p/{id}')
                                       .with_header('X-Issued-At', matcher)
                                       .with_query_param('from', matcher)
                                       .with_cookie('seen', matcher)
      request.path_parameters = [MockServer::KeyToMultiValue.new(name: 'id', values: [matcher])]

      expect(wire(request)).to eq(
        'path' => '/p/{id}',
        'headers' => [{ 'name' => 'X-Issued-At', 'values' => [object] }],
        'queryStringParameters' => [{ 'name' => 'from', 'values' => [object] }],
        'cookies' => { 'seen' => object },
        'pathParameters' => { 'id' => [object] }
      )
    end

    it 'serialises in the keyMatchStyle object form' do
      request = MockServer::HttpRequest.new(headers_key_match_style: 'MATCHING_KEY')
                                       .with_header('X-Issued-At', matcher)

      expect(wire(request)['headers']).to eq({ 'keyMatchStyle' => 'MATCHING_KEY', 'X-Issued-At' => [object] })
    end

    it 'goes builder -> JSON -> model -> JSON unchanged' do
      request = MockServer::HttpRequest.new(path: '/d')
                                       .with_header('X-Issued-At', matcher)
                                       .with_cookie('seen', described_class.before('now').with_not(true))
      json = JSON.generate(request.to_h)
      parsed = MockServer::HttpRequest.from_hash(JSON.parse(json))

      expect(parsed.headers.first.values).to eq([matcher])
      expect(parsed.headers.first.values.first).to be_a(described_class)
      expect(parsed.cookies.first.values).to eq([described_class.before('now').with_not(true)])
      expect(JSON.generate(parsed.to_h)).to eq(json)
    end

    it 'reads every value position into a DateTimeMatcher' do
      request = MockServer::HttpRequest.from_hash(
        'headers' => { 'X-Issued-At' => [object] },
        'queryStringParameters' => [{ 'name' => 'from', 'values' => [object] }],
        'pathParameters' => { 'id' => [object] },
        'cookies' => { 'seen' => object }
      )

      [request.headers, request.query_string_parameters, request.path_parameters, request.cookies].each do |items|
        expect(items.first.values).to eq([matcher])
      end
    end

    it 'reads symbol keys and hashes passed to the constructor' do
      request = MockServer::HttpRequest.new(headers: { 'X-Issued-At' => [{ after: 'now -1 hours', actualFormat: 'rfc1123' }] },
                                            cookies: [{ name: 'seen', value: { before: 'now' } }])

      expect(request.headers.first.values).to eq([matcher])
      expect(request.cookies.first.values).to eq([described_class.before('now')])
    end
  end

  describe 'other value forms stay unchanged' do
    it 'keeps a plain "before:2026-01-01" a plain string' do
      request = MockServer::HttpRequest.from_hash('headers' => { 'X-Plain' => ['before:2026-01-01'] },
                                                  'cookies' => { 'c' => 'after:now' })

      expect(request.headers.first.values).to eq(['before:2026-01-01'])
      expect(request.cookies.first.values).to eq(['after:now'])
      expect(wire(request)).to eq('headers' => [{ 'name' => 'X-Plain', 'values' => ['before:2026-01-01'] }],
                                  'cookies' => { 'c' => 'after:now' })
    end

    it 'keeps the nottable and schema object forms as hashes' do
      nottable = { 'not' => true, 'value' => 'x' }
      schema = { 'schema' => { 'type' => 'string', 'format' => 'date-time' } }
      request = MockServer::HttpRequest.from_hash('headers' => { 'A' => [nottable], 'B' => [schema] })

      expect(request.headers.map(&:values)).to eq([[nottable], [schema]])
      expect(wire(request)['headers']).to eq([{ 'name' => 'A', 'values' => [nottable] },
                                              { 'name' => 'B', 'values' => [schema] }])
    end

    it 'keeps an object it cannot fully carry as a hash, so nothing is lost' do
      two_operators = { 'before' => 'now', 'after' => 'now -1 days' }
      with_style = { 'after' => 'now', 'parameterStyle' => 'SIMPLE' }
      request = MockServer::HttpRequest.from_hash('pathParameters' => { 'a' => [two_operators], 'b' => [with_style] })

      expect(request.path_parameters.map(&:values)).to eq([[two_operators], [with_style]])
      expect(wire(request)['pathParameters']).to eq({ 'a' => [two_operators], 'b' => [with_style] })
    end
  end

  it 'round-trips the shared matcher_request_date_time.json fixture' do
    path = File.expand_path('../../../test-fixtures/expectations/matcher_request_date_time.json', __dir__)
    data = JSON.parse(File.read(path))
    expectation = MockServer::Expectation.from_hash(data)
    request = expectation.http_request

    expect(request.headers.find { |h| h.name == 'X-Issued-At' }.values).to eq([described_class.after('now -1 hours')])
    expect(request.headers.find { |h| h.name == 'X-Plain' }.values).to eq(['before:2026-01-01'])
    expect(request.cookies.first.values.first).to eq(
      described_class.equal_to_date_time('now +1 days').with_not(true)
                     .with_truncate_expected('first day of month').with_truncate_actual('startOfDay')
                     .with_apply_truncation_last(true)
    )
    expect(wire(expectation)['httpRequest']['cookies']).to eq(data['httpRequest']['cookies'])
  end
end
