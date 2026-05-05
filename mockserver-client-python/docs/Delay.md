# Delay

response delay

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**time_unit** | **str** |  | [optional] 
**value** | **int** |  | [optional] 

## Example

```python
from mockserver.models.delay import Delay

# TODO update the JSON string below
json = "{}"
# create an instance of Delay from a JSON string
delay_instance = Delay.from_json(json)
# print the JSON string representation of the object
print(Delay.to_json())

# convert the object into a dict
delay_dict = delay_instance.to_dict()
# create an instance of Delay from a dict
delay_from_dict = Delay.from_dict(delay_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


