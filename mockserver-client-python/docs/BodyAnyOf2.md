# BodyAnyOf2

json schema body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**json_schema** | **object** | JSON Schema (draft-04) | [optional] 

## Example

```python
from mockserver.models.body_any_of2 import BodyAnyOf2

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf2 from a JSON string
body_any_of2_instance = BodyAnyOf2.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf2.to_json())

# convert the object into a dict
body_any_of2_dict = body_any_of2_instance.to_dict()
# create an instance of BodyAnyOf2 from a dict
body_any_of2_from_dict = BodyAnyOf2.from_dict(body_any_of2_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


