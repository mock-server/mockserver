# encoding: UTF-8
#
# The ArrayOf class stores instances of a given class only.
# It enforces this by intercepting the methods :<<, :[]= and :insert on the Array class
# and casts objects to the allowed type first. To use in your code, create a subclass of ArrayOf and override the child_class() method to return
# the class associated with the array.
#
# NOTE: You should use this class internally with the contract that you only call :<<. :[]= and :insert method
# to manipulate the array in use. Can easily be changed to have stricter rules, suffices for internal use in this gem.
#
# @author:: Nayyara Samuel (mailto: nayyara.samuel@opower.com)
#
module MockServer::Model
  DEFAULT_MISSING_INDEX = -1_000_000_000

  # The ArrayOf class stores instances of a given class only.
  class ArrayOf < Array
    alias_method :add_element, :<<
    alias_method :set_element, :[]=
    alias_method :insert_element, :insert

    # Create an array from the elements passed in
    def initialize(items)
      items.each do |item|
        self << item
      end
    end

    # The class/type that this array stores
    def child_class
      fail 'Subclass should override method :child_class'
    end

    # Add the item to the array
    # @param item [Object] an item of the type/class supported by this array
    # @raise [Exception] if the item cannot be converted to the allowed class
    def <<(item)
      add_element(convert_to_child_class(item))
    end

    # Set the given item at the index
    # @param index [Integer] the index for the new element
    # @param item [Object] an item of the type/class supported by this array
    # @raise [Exception] if the item cannot be converted to the allowed class
    def []=(index, item)
      set_element(index, convert_to_child_class(item))
    end

    # Adds the given item at the index and shifts elements forward
    # @param index [Integer] the index for the new element
    # @param item [Object] an item of the type/class supported by this array
    # @raise [Exception] if the item cannot be converted to the allowed class
    def insert(index, item)
      insert_element(index, convert_to_child_class(item))
    end

    # Method to set the element at the specified index.
    # Will insert at index if there is another object at the index; otherwise will update.
    # If the special DEFAULT_MISSING_INDEX value is given, will insert at the end.
    # @param index [Integer] the index for the new element
    # @param item [Object] an item of the type/class supported by this array
    # @raise [Exception] if the item cannot be converted to the allowed class
    def set(index, item)
      if index == DEFAULT_MISSING_INDEX
        self << item
      elsif self[index]
        insert(index, item)
      else
        self[index] = item
      end
    end

    # Cast item to target class
    def convert_to_child_class(item)
      if item && item.class != child_class
        begin
          item = child_class.new(item)
        rescue Exception => e # rubocop:disable Lint/RescueException
          raise "Failed to convert element: #{item} to required type #{child_class}. Error: #{e.message}"
        end
      end
      item
    end
  end
end
