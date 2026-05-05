# HttpTemplate

template to generate response / request

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**delay** | [**Delay**](Delay.md) |  | [optional] 
**template_type** | **str** |  | [optional] 
**template** | **str** |  | [optional] 

## Example

```python
from mockserver.models.http_template import HttpTemplate

# TODO update the JSON string below
json = "{}"
# create an instance of HttpTemplate from a JSON string
http_template_instance = HttpTemplate.from_json(json)
# print the JSON string representation of the object
print(HttpTemplate.to_json())

# convert the object into a dict
http_template_dict = http_template_instance.to_dict()
# create an instance of HttpTemplate from a dict
http_template_from_dict = HttpTemplate.from_dict(http_template_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


