# OpenAPIDefinition

open api or swagger request matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**spec_url_or_payload** | **str** |  | [optional] 
**operation_id** | **str** |  | [optional] 

## Example

```python
from mockserver.models.open_api_definition import OpenAPIDefinition

# TODO update the JSON string below
json = "{}"
# create an instance of OpenAPIDefinition from a JSON string
open_api_definition_instance = OpenAPIDefinition.from_json(json)
# print the JSON string representation of the object
print(OpenAPIDefinition.to_json())

# convert the object into a dict
open_api_definition_dict = open_api_definition_instance.to_dict()
# create an instance of OpenAPIDefinition from a dict
open_api_definition_from_dict = OpenAPIDefinition.from_dict(open_api_definition_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


