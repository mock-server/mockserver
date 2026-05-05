# HttpError

error behaviour

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**drop_connection** | **bool** |  | [optional] 
**response_bytes** | **str** |  | [optional] 

## Example

```python
from mockserver.models.http_error import HttpError

# TODO update the JSON string below
json = "{}"
# create an instance of HttpError from a JSON string
http_error_instance = HttpError.from_json(json)
# print the JSON string representation of the object
print(HttpError.to_json())

# convert the object into a dict
http_error_dict = http_error_instance.to_dict()
# create an instance of HttpError from a dict
http_error_from_dict = HttpError.from_dict(http_error_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


