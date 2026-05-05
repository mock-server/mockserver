# BodyWithContentType

response body

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**var_not** | **bool** |  | [optional] 
**type** | **str** |  | [optional] 
**base64_bytes** | **str** |  | [optional] 
**content_type** | **str** |  | [optional] 
**var_json** | **str** |  | [optional] 
**string** | **str** |  | [optional] 
**xml** | **str** |  | [optional] 

## Example

```python
from mockserver.models.body_with_content_type import BodyWithContentType

# TODO update the JSON string below
json = "{}"
# create an instance of BodyWithContentType from a JSON string
body_with_content_type_instance = BodyWithContentType.from_json(json)
# print the JSON string representation of the object
print(BodyWithContentType.to_json())

# convert the object into a dict
body_with_content_type_dict = body_with_content_type_instance.to_dict()
# create an instance of BodyWithContentType from a dict
body_with_content_type_from_dict = BodyWithContentType.from_dict(body_with_content_type_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


