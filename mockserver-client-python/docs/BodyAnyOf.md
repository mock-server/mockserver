# BodyAnyOf

binary body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**base64_bytes** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_any_of import BodyAnyOf

# TODO update the JSON string below
json = "{}"
# create an instance of BodyAnyOf from a JSON string
body_any_of_instance = BodyAnyOf.from_json(json)
# print the JSON string representation of the object
print(BodyAnyOf.to_json())

# convert the object into a dict
body_any_of_dict = body_any_of_instance.to_dict()
# create an instance of BodyAnyOf from a dict
body_any_of_from_dict = BodyAnyOf.from_dict(body_any_of_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


