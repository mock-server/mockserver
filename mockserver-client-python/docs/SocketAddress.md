# SocketAddress

remote address to send request to, only used for request overrides

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**host** | **str** |  | [optional] 
**port** | **int** |  | [optional] 
**scheme** | **str** |  | [optional] 

## Example

```python
from mockserver.models.socket_address import SocketAddress

# TODO update the JSON string below
json = "{}"
# create an instance of SocketAddress from a JSON string
socket_address_instance = SocketAddress.from_json(json)
# print the JSON string representation of the object
print(SocketAddress.to_json())

# convert the object into a dict
socket_address_dict = socket_address_instance.to_dict()
# create an instance of SocketAddress from a dict
socket_address_from_dict = SocketAddress.from_dict(socket_address_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


