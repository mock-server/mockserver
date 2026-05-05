# ConnectionOptions

connection options

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**suppress_content_length_header** | **bool** |  | [optional] 
**content_length_header_override** | **int** |  | [optional] 
**suppress_connection_header** | **bool** |  | [optional] 
**chunk_size** | **int** |  | [optional] 
**keep_alive_override** | **bool** |  | [optional] 
**close_socket** | **bool** |  | [optional] 
**close_socket_delay** | [**Delay**](Delay.md) |  | [optional] 

## Example

```python
from mockserver.models.connection_options import ConnectionOptions

# TODO update the JSON string below
json = "{}"
# create an instance of ConnectionOptions from a JSON string
connection_options_instance = ConnectionOptions.from_json(json)
# print the JSON string representation of the object
print(ConnectionOptions.to_json())

# convert the object into a dict
connection_options_dict = connection_options_instance.to_dict()
# create an instance of ConnectionOptions from a dict
connection_options_from_dict = ConnectionOptions.from_dict(connection_options_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


