# Body

request body matcher

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**base64_bytes** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 
**var_json** | **str** |  | [optional] 
**match_type** | **str** |  | [optional] 
**match_numbers_as_strings** | **bool** |  | [optional] 
**json_schema** | **object** | JSON Schema (draft-04) | [optional] 
**json_path** | **str** |  | [optional] 
**parameters** | [**KeyToMultiValue**](KeyToMultiValue.md) |  | [optional] 
**regex** | **str** |  | [optional] 
**string** | **str** |  | [optional] 
**sub_string** | **bool** |  | [optional] 
**xml** | **str** |  | [optional] 
**xml_schema** | **str** |  | [optional] 
**xpath** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body import Body

# TODO update the JSON string below
json = "{}"
# create an instance of Body from a JSON string
body_instance = Body.from_json(json)
# print the JSON string representation of the object
print(Body.to_json())

# convert the object into a dict
body_dict = body_instance.to_dict()
# create an instance of Body from a dict
body_from_dict = Body.from_dict(body_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


