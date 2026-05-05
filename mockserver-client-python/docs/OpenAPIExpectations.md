# OpenAPIExpectations

list of open api expectations

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**spec_url_or_payload** | [**OpenAPIExpectationSpecUrlOrPayload**](OpenAPIExpectationSpecUrlOrPayload.md) |  | 
**operations_and_responses** | **Dict[str, str]** |  | [optional] 

## Example

```python
from mockserver.models.open_api_expectations import OpenAPIExpectations

# TODO update the JSON string below
json = "{}"
# create an instance of OpenAPIExpectations from a JSON string
open_api_expectations_instance = OpenAPIExpectations.from_json(json)
# print the JSON string representation of the object
print(OpenAPIExpectations.to_json())

# convert the object into a dict
open_api_expectations_dict = open_api_expectations_instance.to_dict()
# create an instance of OpenAPIExpectations from a dict
open_api_expectations_from_dict = OpenAPIExpectations.from_dict(open_api_expectations_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


