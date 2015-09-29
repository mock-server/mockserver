# encoding: UTF-8
require_relative './body'
require_relative './delay'
require_relative './header'
require_relative './cookie'
require 'base64'

#
# A class to model a response in an expectation.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Model for a mock response
  class Response < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :status_code, default: 200
    property :cookies, default: Cookies.new([])
    property :headers, default: Headers.new([])
    property :delay
    property :body

    coerce_key :cookies, Cookies
    coerce_key :headers, Headers
    coerce_key :delay, Delay
    coerce_key :body, String
  end

  # DSL Methods for a response
  module DSL
    def response(&_)
      obj = Response.new
      yield obj if block_given?
      obj
    end

    def decode(string)
      Base64.decode64(string)
    end

    alias_method :http_response, :response
  end
end
