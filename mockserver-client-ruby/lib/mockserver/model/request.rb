# encoding: UTF-8
require 'hashie'
require_relative './parameter'
require_relative './body'
require_relative './enum'
require 'base64'
require 'active_support/core_ext/module'
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
    include Hashie::Extensions::Dash::PropertyTranslation
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    ALLOWED_METHODS = [:GET, :POST, :PUT, :DELETE]

    alias_attribute :query_parameters, :query_string_parameters

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
  end

  # DSL methods related to requests
  module DSL
    def request(method, path, &_)
      obj = Request.new(method: method, path: path)
      yield obj if block_given?
      obj
    end

    alias_method :http_request, :request
  end
end
