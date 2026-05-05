# OpenAPIExpectation

open api or swagger expectation

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**spec_url_or_payload** | [**OpenAPIExpectationSpecUrlOrPayload**](OpenAPIExpectationSpecUrlOrPayload.md) |  | 
**operations_and_responses** | **Dict[str, str]** |  | [optional] 

## Example

```python
from mockserver.models.open_api_expectation import OpenAPIExpectation

# TODO update the JSON string below
json = "{}"
# create an instance of OpenAPIExpectation from a JSON string
open_api_expectation_instance = OpenAPIExpectation.from_json(json)
# print the JSON string representation of the object
print(OpenAPIExpectation.to_json())

# convert the object into a dict
open_api_expectation_dict = open_api_expectation_instance.to_dict()
# create an instance of OpenAPIExpectation from a dict
open_api_expectation_from_dict = OpenAPIExpectation.from_dict(open_api_expectation_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


