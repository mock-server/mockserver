# encoding: UTF-8
require 'hashie'
require_relative './enum'

#
# A class to model the number of times an expectation should be respected.
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Enum for boolean values since Ruby does not have this by default
  class Boolean < Enum
    def allowed_values
      [true, false]
    end

    def !
      !@value
    end
  end

  # Model for times class
  class Times < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :remaining_times, default: 0
    property :unlimited, default: false

    coerce_key :unlimited, Boolean
  end

  # DSL methods related to times
  module DSL
    def unlimited
      Times.new(unlimited: true)
    end

    def once
      Times.new(remaining_times: 1)
    end

    def exactly(num)
      Times.new(remaining_times: num)
    end

    def at_least(num)
      Times.new(remaining_times: num, unlimited: true)
    end

    def times(&_)
      obj = once
      yield obj if block_given?
      obj
    end
  end
end
