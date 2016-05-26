# encoding: UTF-8
require 'rest-client'
require 'logging_factory'
require_relative './model/times'
require_relative './model/request'
require_relative './model/expectation'
require_relative './utility_methods'

# An abstract client for making requests supported by mock and proxy client.
# @author Nayyara Samuel(mailto: nayyara.samuel@opower.com)
#
module MockServer
  RESET_ENDPOINT    = '/reset'
  CLEAR_ENDPOINT    = '/clear'
  RETRIEVE_ENDPOINT = '/retrieve'
  DUMP_LOG_ENDPOINT = '/dumpToLog'

  # An abstract client for making requests supported by mock and proxy client.
  class AbstractClient
    include Model::DSL
    include Model
    include UtilityMethods

    attr_accessor :logger

    def initialize(host, port)
      fail 'Cannot instantiate AbstractClient class. You must subclass it.' if self.class == AbstractClient
      fail 'Host/port must not be nil' unless host && port
      protocol = ('https' if port == 443) || 'http'
      @base   = RestClient::Resource.new("#{protocol}://#{host}:#{port}", headers: { 'Content-Type' => 'application/json' })
      @logger = ::LoggingFactory::DEFAULT_FACTORY.log(self.class)
    end

    # Clear all expectations with the given request
    # @param request [Request] the request to use to clear an expectation
    # @return [Object] the response from the clear action
    def clear(request)
      request = camelized_hash(HTTP_REQUEST => Request.new(symbolize_keys(request)))

      logger.debug("Clearing expectation with request: #{request}")
      logger.debug("URL: #{CLEAR_ENDPOINT}. Payload: #{request.to_hash}")

      response = @base[CLEAR_ENDPOINT].put(request.to_json, content_type: :json)
      logger.debug("Got clear response: #{response.code}")
      parse_string_to_json(response)
    end

    # Reset the mock server clearing all expectations previously registered
    # @return [Object] the response from the reset action
    def reset
      request = {}

      logger.debug('Resetting mockserver')
      logger.debug("URL: #{RESET_ENDPOINT}. Payload: #{request.to_hash}")

      response = @base[RESET_ENDPOINT].put(request.to_json)
      logger.debug("Got reset response: #{response.code}")
      parse_string_to_json(response)
    end

    # Retrieve the list of requests that have been processed by the server
    # @param request [Request] to filter requests
    # @return [Object] the list of responses processed by the server
    def retrieve(request = nil)
      request = request ? camelized_hash(HTTP_REQUEST => Request.new(symbolize_keys(request))) : {}

      logger.debug('Retrieving request list from mockserver')
      logger.debug("URL: #{RETRIEVE_ENDPOINT}. Payload: #{request.to_hash}")

      response = @base[RETRIEVE_ENDPOINT].put(request.to_json)
      logger.debug("Got retrieve response: #{response.code}")
      requests = Requests.new([])
      parse_string_to_json(response.body).map { |result| requests << request_from_json(result) } unless response.empty?
      requests.code = response.code
      requests
    end

    # Request to dump logs to file
    # @param java [Boolean] true to dump as Java code; false to dump as JSON
    # @return [Object] the list of responses processed by the server
    def dump_log(request = nil, java = false)
      type_params = java ? '?type=java' : ''
      url         = "#{DUMP_LOG_ENDPOINT}#{type_params}"
      request     = request ? Request.new(symbolize_keys(request)) : {}

      logger.debug('Sending dump log request to mockserver')
      logger.debug("URL: #{url}. Payload: #{request.to_hash}")

      response = @base[url].put(request.to_json)
      logger.debug("Got dump to log response: #{response.code}")
      parse_string_to_json(response)
    end

    # Verify that the given request is called the number of times expected
    # @param request [Request] to filter requests
    # @param times [Times] expected number of times
    # @return [Object] the list of responses processed by the server that match the request
    def verify(request, times = exactly(1))
      logger.debug('Sending query for verify to mockserver')
      results   = retrieve(request)

      # Reusing the times model here so interpreting values here
      times     = Times.new(symbolize_keys(times))
      num_times = times.remaining_times
      is_exact  = !times.unlimited

      fulfilled = is_exact ? (num_times == results.size) : (num_times <= results.size)
      fail "Expected request to be present: [#{num_times}] (#{is_exact ? 'exactly' : 'at least'}). But found: [#{results.size}]" unless fulfilled
      results
    end
  end
end
