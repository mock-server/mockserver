# Expectation

expectation

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**id** | **str** |  | [optional] 
**priority** | **int** |  | [optional] 
**http_request** | [**RequestDefinition**](RequestDefinition.md) |  | [optional] 
**http_response** | [**HttpResponse**](HttpResponse.md) |  | [optional] 
**http_response_template** | [**HttpTemplate**](HttpTemplate.md) |  | [optional] 
**http_response_class_callback** | [**HttpClassCallback**](HttpClassCallback.md) |  | [optional] 
**http_response_object_callback** | [**HttpObjectCallback**](HttpObjectCallback.md) |  | [optional] 
**http_forward** | [**HttpForward**](HttpForward.md) |  | [optional] 
**http_forward_template** | [**HttpTemplate**](HttpTemplate.md) |  | [optional] 
**http_forward_class_callback** | [**HttpClassCallback**](HttpClassCallback.md) |  | [optional] 
**http_forward_object_callback** | [**HttpObjectCallback**](HttpObjectCallback.md) |  | [optional] 
**http_override_forwarded_request** | [**HttpOverrideForwardedRequest**](HttpOverrideForwardedRequest.md) |  | [optional] 
**http_error** | [**HttpError**](HttpError.md) |  | [optional] 
**times** | [**Times**](Times.md) |  | [optional] 
**time_to_live** | [**TimeToLive**](TimeToLive.md) |  | [optional] 

## Example

```python
from mockserver.models.expectation import Expectation

# TODO update the JSON string below
json = "{}"
# create an instance of Expectation from a JSON string
expectation_instance = Expectation.from_json(json)
# print the JSON string representation of the object
print(Expectation.to_json())

# convert the object into a dict
expectation_dict = expectation_instance.to_dict()
# create an instance of Expectation from a dict
expectation_from_dict = Expectation.from_dict(expectation_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


