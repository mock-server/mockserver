# encoding: UTF-8
require_relative './model/expectation'
require_relative './abstract_client'
require_relative './utility_methods'

#
# The client used to interact with the mock server.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer
  EXPECTATION_ENDPOINT = '/expectation'

  # The client used to interact with the mock server.
  class MockServerClient < AbstractClient
    include Model
    include UtilityMethods

    # Registers an expectation with the mockserver
    # @param expectation [Expectation] the expectation to create the request from
    # @return [Object] the response from the register action
    def register(expectation)
      fail 'Expectation passed in is not valid type' unless expectation.is_a?(Expectation)
      request = create_expectation_request(expectation)

      logger.debug('Registering new expectation')
      logger.debug("URL: #{EXPECTATION_ENDPOINT} Payload: #{request.to_hash}")

      response = @base[EXPECTATION_ENDPOINT].put(request.to_json, content_type: :json)
      logger.debug("Got register response: #{response.code}")
      parse_string_to_json(response)
    end

    private

    # Create an expecation request to send to the expectation endpoint of
    # @param expectation [Expectation] the expectation  to create the request from
    # @return [Hash] a hash representing the request to use in registering an expectation with the mock server
    # rubocop:disable Lint/LiteralInInterpolation
    def create_expectation_request(expectation)
      expectation_request = camelized_hash(expectation)
      logger.debug("Expectation JSON: #{expectation_request.to_json}")
      fail "You can only set either of #{[HTTP_RESPONSE, HTTP_FORWARD]}. But not both" if expectation_request[HTTP_RESPONSE] && expectation_request[HTTP_FORWARD]
      expectation_request
    end
  end
end
