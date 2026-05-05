# Times

number of responses

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**remaining_times** | **int** |  | [optional] 
**unlimited** | **bool** |  | [optional] 

## Example

```python
from mockserver.models.times import Times

# TODO update the JSON string below
json = "{}"
# create an instance of Times from a JSON string
times_instance = Times.from_json(json)
# print the JSON string representation of the object
print(Times.to_json())

# convert the object into a dict
times_dict = times_instance.to_dict()
# create an instance of Times from a dict
times_from_dict = Times.from_dict(times_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


