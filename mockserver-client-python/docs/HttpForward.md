# HttpForward

host and port to forward to

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**host** | **str** |  | [optional] 
**port** | **int** |  | [optional] 
**scheme** | **str** |  | [optional] 

## Example

```python
from mockserver.models.http_forward import HttpForward

# TODO update the JSON string below
json = "{}"
# create an instance of HttpForward from a JSON string
http_forward_instance = HttpForward.from_json(json)
# print the JSON string representation of the object
print(HttpForward.to_json())

# convert the object into a dict
http_forward_dict = http_forward_instance.to_dict()
# create an instance of HttpForward from a dict
http_forward_from_dict = HttpForward.from_dict(http_forward_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


