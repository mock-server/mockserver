# RequestDefinition

request definition

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**secure** | **bool** |  | [optional] 
**keep_alive** | **bool** |  | [optional] 
**method** | [**StringOrJsonSchema**](StringOrJsonSchema.md) |  | [optional] 
**path** | [**StringOrJsonSchema**](StringOrJsonSchema.md) |  | [optional] 
**path_parameters** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**query_string_parameters** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**body** | [**Body**](Body.md) |  | [optional] 
**headers** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**cookies** | [**KeyToValue**](KeyToValue.md) |  | [optional] 
**socket_address** | [**SocketAddress**](SocketAddress.md) |  | [optional] 
**protocol** | [**Protocol**](Protocol.md) |  | [optional] 
**spec_url_or_payload** | **str** |  | [optional] 
**operation_id** | **str** |  | [optional] 

## Example

```python
from mockserver.models.request_definition import RequestDefinition

# TODO update the JSON string below
json = "{}"
# create an instance of RequestDefinition from a JSON string
request_definition_instance = RequestDefinition.from_json(json)
# print the JSON string representation of the object
print(RequestDefinition.to_json())

# convert the object into a dict
request_definition_dict = request_definition_instance.to_dict()
# create an instance of RequestDefinition from a dict
request_definition_from_dict = RequestDefinition.from_dict(request_definition_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


