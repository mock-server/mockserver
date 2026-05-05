# MockserverClearPutRequest


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
**id** | **str** |  | 

## Example

```python
from mockserver.models.mockserver_clear_put_request import MockserverClearPutRequest

# TODO update the JSON string below
json = "{}"
# create an instance of MockserverClearPutRequest from a JSON string
mockserver_clear_put_request_instance = MockserverClearPutRequest.from_json(json)
# print the JSON string representation of the object
print(MockserverClearPutRequest.to_json())

# convert the object into a dict
mockserver_clear_put_request_dict = mockserver_clear_put_request_instance.to_dict()
# create an instance of MockserverClearPutRequest from a dict
mockserver_clear_put_request_from_dict = MockserverClearPutRequest.from_dict(mockserver_clear_put_request_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


