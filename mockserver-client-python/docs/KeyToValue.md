# KeyToValue


## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------

## Example

```python
from mockserver.models.key_to_value import KeyToValue

# TODO update the JSON string below
json = "{}"
# create an instance of KeyToValue from a JSON string
key_to_value_instance = KeyToValue.from_json(json)
# print the JSON string representation of the object
print(KeyToValue.to_json())

# convert the object into a dict
key_to_value_dict = key_to_value_instance.to_dict()
# create an instance of KeyToValue from a dict
key_to_value_from_dict = KeyToValue.from_dict(key_to_value_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


