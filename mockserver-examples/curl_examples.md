#### expectation with special characters

###### expectation

```bash
curl --location --request PUT 'localhost:1080/mockserver/expectation' \
--header 'Content-Type: application/json' \
--data-raw '{
    "id": "hello", 
    "httpRequest": {
        "path": "/", 
        "method": "GET"
    }, 
    "httpResponse": {
        "statusCode": 200, 
        "headers": [
            {
                "name": "Content-Type", 
                "values": [
                    "application/json;charset=utf-8"
                ]
            }
        ], 
        "body": {
            "k": "Hello世界"
        }
    }, 
    "priority": 10
}'
```

###### matching request

```bash
curl 'localhost:1080/'  
```

#### expectation with special characters

###### expectation

```bash
curl -X PUT 'localhost:1080/mockserver/expectation' \
-d '{
    "httpRequest": {
        "method": "PUT", 
        "path": "/continueCase", 
        "body": {
            "callForSignatureTask": {
                "expectedDueTime": 1, 
                "expectedDueTimeUnit": "minute", 
                "caseDueDate": "30-11-2021"
            }
        }
    }, 
    "httpResponse": {
        "statusCode": 200
    }
}'
```

###### matching request

```bash
curl -X PUT 'localhost:1080/continueCase' \
--header 'Content-Type: application/json' \
-d '{
    "callForSignatureTask": {
        "expectedDueTime": 1, 
        "expectedDueTimeUnit": "minute", 
        "caseDueDate": "30-11-2021"
    }
}'
```

