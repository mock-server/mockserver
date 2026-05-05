# TimeToLive

time expectation is valid for

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**time_unit** | **str** |  | [optional] 
**time_to_live** | **int** |  | [optional] 
**unlimited** | **bool** |  | [optional] 

## Example

```python
from mockserver.models.time_to_live import TimeToLive

# TODO update the JSON string below
json = "{}"
# create an instance of TimeToLive from a JSON string
time_to_live_instance = TimeToLive.from_json(json)
# print the JSON string representation of the object
print(TimeToLive.to_json())

# convert the object into a dict
time_to_live_dict = time_to_live_instance.to_dict()
# create an instance of TimeToLive from a dict
time_to_live_from_dict = TimeToLive.from_dict(time_to_live_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


