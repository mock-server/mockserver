# HttpRequest

request properties matcher

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

## Example

```python
from mockserver.models.http_request import HttpRequest

# TODO update the JSON string below
json = "{}"
# create an instance of HttpRequest from a JSON string
http_request_instance = HttpRequest.from_json(json)
# print the JSON string representation of the object
print(HttpRequest.to_json())

# convert the object into a dict
http_request_dict = http_request_instance.to_dict()
# create an instance of HttpRequest from a dict
http_request_from_dict = HttpRequest.from_dict(http_request_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


