# HttpClassCallback

class callback

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**callback_class** | **str** |  | [optional] 

## Example

```python
from mockserver.models.http_class_callback import HttpClassCallback

# TODO update the JSON string below
json = "{}"
# create an instance of HttpClassCallback from a JSON string
http_class_callback_instance = HttpClassCallback.from_json(json)
# print the JSON string representation of the object
print(HttpClassCallback.to_json())

# convert the object into a dict
http_class_callback_dict = http_class_callback_instance.to_dict()
# create an instance of HttpClassCallback from a dict
http_class_callback_from_dict = HttpClassCallback.from_dict(http_class_callback_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


