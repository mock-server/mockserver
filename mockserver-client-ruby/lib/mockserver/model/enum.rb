# encoding: UTF-8
#
# A class to model a Java-like Enum.
# To create an Enum extend this class and override :allowed_values method with allowed enum values.
#
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  # Enum generic class
  class Enum
    # Create an instance of the enum from the value supplied
    # @param supplied_value [Object] value used to create instance of an enum
    # @raise [Exception] if the supplied value is not valid for this enum
    def initialize(supplied_value)
      supplied_value = pre_process_value(supplied_value)
      fail "Supplied value: #{supplied_value} is not valid. Allowed values are: #{allowed_values.inspect}" unless allowed_values.include?(supplied_value)
      @value = supplied_value
    end

    # @return [Array] a list of values allowed by this enum
    def allowed_values
      fail 'Override :allowed_values in Enum class'
    end

    # A pre-process hook for a value before it is stored
    # @param value [Object] a value used to instantiate the enum
    # @return [Object] the processed value. By default, a no-op implementation.
    def pre_process_value(value)
      value
    end

    # Override this for JSON representation
    def to_s
      @value.to_s
    end
  end

  # Subclass of Enum that has a list of symbols as allowed values.
  class SymbolizedEnum < Enum
    # Pre-process the value passed in and convert to a symbol
    # @param value [Object] a value used to instantiate the enum
    # @return [Symbol] a symbolized version of the value passed in (first calls to_s)
    def pre_process_value(value)
      value.to_s.to_sym
    end
  end
end
