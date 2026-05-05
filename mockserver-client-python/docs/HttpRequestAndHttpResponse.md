# HttpRequestAndHttpResponse

request and response

## Properties

Name | Type | Description | Notes
------------ | ------------- | ------------- | -------------
**http_request** | [**HttpRequest**](HttpRequest.md) |  | [optional] 
**http_response** | [**HttpResponse**](HttpResponse.md) |  | [optional] 
**timestamp** | **str** |  | [optional] 

## Example

```python
from mockserver.models.http_request_and_http_response import HttpRequestAndHttpResponse

# TODO update the JSON string below
json = "{}"
# create an instance of HttpRequestAndHttpResponse from a JSON string
http_request_and_http_response_instance = HttpRequestAndHttpResponse.from_json(json)
# print the JSON string representation of the object
print(HttpRequestAndHttpResponse.to_json())

# convert the object into a dict
http_request_and_http_response_dict = http_request_and_http_response_instance.to_dict()
# create an instance of HttpRequestAndHttpResponse from a dict
http_request_and_http_response_from_dict = HttpRequestAndHttpResponse.from_dict(http_request_and_http_response_dict)
```
[[Back to Model list]](../README.md#documentation-for-models) [[Back to API list]](../README.md#documentation-for-api-endpoints) [[Back to README]](../README.md)


