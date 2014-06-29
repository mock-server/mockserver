# encoding: UTF-8
require 'hashie'
require_relative './enum'

#
# A class to model a forwarding on a request.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Enum for a scheme used in a forward request
  class Scheme < SymbolizedEnum
    def allowed_values
      [:HTTP, :HTTPS]
    end
  end

  # Model for forwarding
  class Forward < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :host, default: 'localhost'
    property :port, default: 80
    property :scheme, default: 'HTTP'

    coerce_key :host, String
    coerce_key :scheme, Scheme
  end

  # DSL methods for forward
  module DSL
    def forward(&_)
      obj = Forward.new
      yield obj if block_given?
      obj
    end

    alias_method :http_forward, :forward
  end
end
