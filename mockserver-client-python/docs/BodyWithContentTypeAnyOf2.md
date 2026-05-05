# BodyWithContentTypeAnyOf2

string response body

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**string** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_with_content_type_any_of2 import BodyWithContentTypeAnyOf2

# TODO update the JSON string below
json = "{}"
# create an instance of BodyWithContentTypeAnyOf2 from a JSON string
body_with_content_type_any_of2_instance = BodyWithContentTypeAnyOf2.from_json(json)
# print the JSON string representation of the object
print(BodyWithContentTypeAnyOf2.to_json())

# convert the object into a dict
body_with_content_type_any_of2_dict = body_with_content_type_any_of2_instance.to_dict()
# create an instance of BodyWithContentTypeAnyOf2 from a dict
body_with_content_type_any_of2_from_dict = BodyWithContentTypeAnyOf2.from_dict(body_with_content_type_any_of2_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


