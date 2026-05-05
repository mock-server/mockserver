# HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**add** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**replace** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**remove** | **List[str]** |  | [optional] 

## Example

```python
from mockserver.models.http_override_forwarded_request_one_of_request_modifier_query_string_parameters import HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters

# TODO update the JSON string below
json = "{}"
# create an instance of HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters from a JSON string
http_override_forwarded_request_one_of_request_modifier_query_string_parameters_instance = HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters.from_json(json)
# print the JSON string representation of the object
print(HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters.to_json())

# convert the object into a dict
http_override_forwarded_request_one_of_request_modifier_query_string_parameters_dict = http_override_forwarded_request_one_of_request_modifier_query_string_parameters_instance.to_dict()
# create an instance of HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters from a dict
http_override_forwarded_request_one_of_request_modifier_query_string_parameters_from_dict = HttpOverrideForwardedRequestOneOfRequestModifierQueryStringParameters.from_dict(http_override_forwarded_request_one_of_request_modifier_query_string_parameters_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


