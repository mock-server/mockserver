# encoding: UTF-8
require 'hashie'
require_relative './parameter'
require_relative './body'
require_relative './enum'
require 'base64'
#
# A class to model a request in an expectation.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Enum for HTTP methods
  class HTTPMethod < SymbolizedEnum
    def allowed_values
      [:GET, :POST, :PUT, :DELETE]
    end
  end

  # Request model
  class Request < Hashie::Trash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    ALLOWED_METHODS = [:GET, :POST, :PUT, :DELETE]

    property :method, required: true, default: :GET
    property :path, required: true, default: ''
    property :query_string_parameters, default: Parameters.new([])
    property :cookies, default: Parameters.new([])
    property :headers, default: Parameters.new([])
    property :body, transform_with: (lambda do |body|
      is_base_64_body = body && body.type == :BINARY
      body_value = is_base_64_body ? Base64.decode64(body.value) : body.value
      Body.new(type: :STRING, value: body_value)
    end)

    coerce_key :method, HTTPMethod
    coerce_key :path, String
    coerce_key :query_string_parameters, Parameters
    coerce_key :cookies, Parameters
    coerce_key :headers, Parameters
    coerce_key :body, Body

    # Creates a request from a hash
    # @param payload [Hash] a hash representation of the request
    def populate_from_payload(payload)
      @request = payload[MockServer::HTTP_REQUEST]
      @request = Request.new(symbolize_keys(@request)) if @request
    end
  end

  # Class to store a list of mocks - useful for modeling retrieve endpoint result
  class Requests < ArrayOf
    # Code is used to store HTTP status code returned from retrieve endpoint
    attr_accessor :code

    def child_class
      Request
    end
  end

  # DSL methods related to requests
  module DSL
    def request(method, path, &_)
      obj = Request.new(method: method, path: path)
      yield obj if block_given?
      obj
    end

    def request_from_json(payload)
      request = Request.new(symbolize_keys(payload))
      yield request if block_given?
      request
    end

    alias_method :http_request, :request
  end
end
