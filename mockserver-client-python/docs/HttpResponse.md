# HttpResponse

response to return

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**body** | [**BodyWithContentType**](BodyWithContentType.md) |  | [optional] 
**cookies** | [**KeyToValue**](KeyToValue.md) |  | [optional] 
**connection_options** | [**ConnectionOptions**](ConnectionOptions.md) |  | [optional] 
**headers** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**status_code** | **int** |  | [optional] 
**reason_phrase** | **str** |  | [optional] 

## Example

```python
from mockserver.models.http_response import HttpResponse

# TODO update the JSON string below
json = "{}"
# create an instance of HttpResponse from a JSON string
http_response_instance = HttpResponse.from_json(json)
# print the JSON string representation of the object
print(HttpResponse.to_json())

# convert the object into a dict
http_response_dict = http_response_instance.to_dict()
# create an instance of HttpResponse from a dict
http_response_from_dict = HttpResponse.from_dict(http_response_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


