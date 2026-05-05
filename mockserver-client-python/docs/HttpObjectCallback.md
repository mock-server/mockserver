# HttpObjectCallback

object / method callback

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**client_id** | **str** |  | [optional] 
**response_callback** | **bool** |  | [optional] 

## Example

```python
from mockserver.models.http_object_callback import HttpObjectCallback

# TODO update the JSON string below
json = "{}"
# create an instance of HttpObjectCallback from a JSON string
http_object_callback_instance = HttpObjectCallback.from_json(json)
# print the JSON string representation of the object
print(HttpObjectCallback.to_json())

# convert the object into a dict
http_object_callback_dict = http_object_callback_instance.to_dict()
# create an instance of HttpObjectCallback from a dict
http_object_callback_from_dict = HttpObjectCallback.from_dict(http_object_callback_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


