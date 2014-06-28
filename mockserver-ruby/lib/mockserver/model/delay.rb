# encoding: UTF-8
require_relative './enum'

#
# A model for a delay in a response.
# @author: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Enum for time unit
  class TimeUnit < SymbolizedEnum
    def allowed_values
      [:NANOSECONDS, :MICROSECONDS, :MILLISECONDS, :SECONDS, :MINUTES, :HOURS, :DAYS]
    end
  end

  # Model a delay object
  class Delay < Hashie::Dash
    include Hashie::Extensions::MethodAccess
    include Hashie::Extensions::IgnoreUndeclared
    include Hashie::Extensions::Coercion

    property :time_unit, default: 'SECONDS'
    property :value, required: true

    coerce_key :time_unit, TimeUnit
  end

  # DSL methods related to delay model
  module DSL
    def delay_by(time_unit, value)
      Delay.new(time_unit: time_unit, value: value)
    end
  end
end
