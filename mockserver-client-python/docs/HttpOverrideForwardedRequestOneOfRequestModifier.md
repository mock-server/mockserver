# HttpOverrideForwardedRequestOneOfRequestModifier


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**path** | [**HttpOverrideForwardedRequestOneOfRequestModifierPath**](HttpOverrideForwardedRequestOneOfRequestModifierPath.md) |  | [optional] 
**query_string_parameters** | [**HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters**](HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters.md) |  | [optional] 
**headers** | [**HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters**](HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters.md) |  | [optional] 
**cookies** | [**HttpOverrideForwardedRequestOneOfRequestModifierCookies**](HttpOverrideForwardedRequestOneOfRequestModifierCookies.md) |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request_one_of_request_modifier import HttpOverrideForwardedRequestOneOfRequestModifier

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequestOneOfRequestModifier from a JSON string
http_override_forwarded_request_one_of_request_modifier_instance = HttpOverrideForwardedRequestOneOfRequestModifier.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequestOneOfRequestModifier.to_json())

# convert the object into a dict
http_override_forwarded_request_one_of_request_modifier_dict = http_override_forwarded_request_one_of_request_modifier_instance.to_dict()
# create an instance of HttpOverrideForwardedRequestOneOfRequestModifier from a dict
http_override_forwarded_request_one_of_request_modifier_from_dict = HttpOverrideForwardedRequestOneOfRequestModifier.from_dict(http_override_forwarded_request_one_of_request_modifier_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


