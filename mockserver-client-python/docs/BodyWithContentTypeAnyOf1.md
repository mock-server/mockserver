# BodyWithContentTypeAnyOf1

json response body

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**var_json** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_with_content_type_any_of1 import BodyWithContentTypeAnyOf1

# TODO update the JSON string below
json = "{}"
# create an instance of BodyWithContentTypeAnyOf1 from a JSON string
body_with_content_type_any_of1_instance = BodyWithContentTypeAnyOf1.from_json(json)
# print the JSON string representation of the object
print(BodyWithContentTypeAnyOf1.to_json())

# convert the object into a dict
body_with_content_type_any_of1_dict = body_with_content_type_any_of1_instance.to_dict()
# create an instance of BodyWithContentTypeAnyOf1 from a dict
body_with_content_type_any_of1_from_dict = BodyWithContentTypeAnyOf1.from_dict(body_with_content_type_any_of1_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


