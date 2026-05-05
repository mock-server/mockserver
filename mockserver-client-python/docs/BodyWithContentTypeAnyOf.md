# BodyWithContentTypeAnyOf

binary response body

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**base64_bytes** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_with_content_type_any_of import BodyWithContentTypeAnyOf

# TODO update the JSON string below
json = "{}"
# create an instance of BodyWithContentTypeAnyOf from a JSON string
body_with_content_type_any_of_instance = BodyWithContentTypeAnyOf.from_json(json)
# print the JSON string representation of the object
print(BodyWithContentTypeAnyOf.to_json())

# convert the object into a dict
body_with_content_type_any_of_dict = body_with_content_type_any_of_instance.to_dict()
# create an instance of BodyWithContentTypeAnyOf from a dict
body_with_content_type_any_of_from_dict = BodyWithContentTypeAnyOf.from_dict(body_with_content_type_any_of_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


