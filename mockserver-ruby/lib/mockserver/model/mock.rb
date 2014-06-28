# encoding: UTF-8
require_relative './array_of'
require_relative './request'
require_relative './response'
require_relative './forward'
require_relative './times'
require_relative '../utility_methods'

#
# A class to model a mock object used to create and retrieve mock requests.
# See http://www.mock-server.com/#create-expectations for details.
#
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer
  HTTP_REQUEST = 'httpRequest'
  HTTP_RESPONSE = 'httpResponse'
  HTTP_FORWARD = 'httpForward'
  HTTP_TIMES = 'times'

  # Models
  module Model # rubocop:disable Style/ClassAndModuleChildren
    # Mock object model
    class Mock
      include MockServer::UtilityMethods
      attr_accessor :times

      # Creates an expectation from a hash
      # @param payload [Hash] a hash representation of the expectation
      def populate_from_payload(payload)
        @request = payload[MockServer::HTTP_REQUEST]
        @request = Request.new(symbolize_keys(@request)) if @request

        @response = payload[MockServer::HTTP_RESPONSE]
        @response = Response.new(symbolize_keys(@response)) if @response

        @forward = payload[MockServer::HTTP_FORWARD]
        @forward = Forward.new(symbolize_keys(@forward)) if @forward

        @times = payload[MockServer::HTTP_TIMES]
        @times = Times.new(symbolize_keys(@times)) if @times
      end

      # Method to setup the request on the expectation object
      # @yieldparam [Request] the request that this expectation references
      # @return [Expectation] this object according to the  the builder pattern
      def request(&_)
        if block_given?
          @request ||= Request.new
          yield @request
        end
        @request
      end

      # Method to setup the response on the expectation object
      # @yieldparam [Response] the response that this expectation references
      # @return [Expectation] this object according to the  the builder pattern
      def response(&_)
        if block_given?
          @response ||= Response.new
          yield @response
        end
        @response
      end

      # Method to setup the request on the expectation object
      # @yieldparam [Forward] the forward object that this expectation references
      # @return [Expectation] this object according to the  the builder pattern
      def forward(&_)
        if block_given?
          @forward ||= Forward.new
          yield @forward
        end
        @forward
      end

      # Setter for request
      # @param request [Request] a request object
      def request=(request)
        @request = Request.new(request)
      end

      # Setter for response
      # @param response [Response] a response object
      def response=(response)
        @response = Response.new(response)
      end

      # Setter for forward
      # @param forward [Forward] a forward object
      def forward=(forward)
        @forward = Forward.new(forward)
      end

      # Override to_json method
      # @return [String] the json representation for this object
      def to_json(*p)
        to_hash.to_json(*p)
      end

      # Convert to hash
      # @return [Hash] the hash representation for this object
      def to_hash
        {
          MockServer::HTTP_REQUEST => @request,
          MockServer::HTTP_RESPONSE => @response,
          MockServer::HTTP_FORWARD => @forward,
          MockServer::HTTP_TIMES => @times
        }
      end
    end

    # Class to store a list of mocks - useful for modeling retrieve endpoint result
    class Mocks < ArrayOf
      # Code is used to store HTTP status code returned from retrieve endpoint
      attr_accessor :code

      def child_class
        Mock
      end
    end
  end
end
