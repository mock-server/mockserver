# frozen_string_literal: true

require 'json'

RSpec.describe 'JSONPath and XPath body date-time comparisons' do
  it 'writes a date-time comparison on a JSONPath body' do
    body = MockServer::Body.json_path('$.order.created',
                                      date_time: MockServer::DateTimeMatcher.after('now -1 days').with_zone('Europe/London'))

    expect(MockServer.serialize_body(body)).to eq(
      'type' => 'JSON_PATH', 'jsonPath' => '$.order.created',
      'dateTime' => { 'after' => 'now -1 days', 'zone' => 'Europe/London' }
    )
  end

  it 'writes a date-time comparison on an XPath body' do
    body = MockServer::Body.xpath('/order/@expires', date_time: MockServer::DateTimeMatcher.before('2026-01-01').with_not(true))

    expect(MockServer.serialize_body(body)).to eq(
      'type' => 'XPATH', 'xpath' => '/order/@expires', 'dateTime' => { 'not' => true, 'before' => '2026-01-01' }
    )
  end

  it 'writes nothing extra without a date-time comparison' do
    expect(MockServer.serialize_body(MockServer::Body.json_path('$.a'))).to eq('type' => 'JSON_PATH', 'jsonPath' => '$.a')
    expect(MockServer.serialize_body(MockServer::Body.xpath('/a'))).to eq('type' => 'XPATH', 'xpath' => '/a')
  end

  it 'reads a body date-time comparison as a DateTimeMatcher and writes it back' do
    data = { 'type' => 'JSON_PATH', 'jsonPath' => '$.created',
             'dateTime' => { 'equalToDateTime' => 'now', 'truncateActual' => 'startOfDay' } }
    body = MockServer.deserialize_body(data)

    expect(body.date_time).to eq(MockServer::DateTimeMatcher.equal_to_date_time('now').with_truncate_actual('startOfDay'))
    expect(MockServer.serialize_body(body)).to eq(data)
  end

  %w[matcher_body_json_path_date_time.json matcher_body_xpath_date_time.json].each do |name|
    it "round-trips the shared fixture #{name} exactly" do
      data = JSON.parse(File.read(File.expand_path("../../../test-fixtures/expectations/#{name}", __dir__)))
      expectation = MockServer::Expectation.from_hash(data)

      expect(expectation.http_request.body.date_time).to be_a(MockServer::DateTimeMatcher)
      expect(JSON.parse(JSON.generate(expectation.to_h))['httpRequest']['body']).to eq(data['httpRequest']['body'])
    end
  end
end
