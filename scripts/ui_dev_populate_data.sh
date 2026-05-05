#!/usr/bin/env bash

set -e

MOCKSERVER_HOST="localhost"
MOCKSERVER_PORT=1080
VERBOSE=false

show_help() {
  cat <<EOF
Usage: ./scripts/ui_dev_populate_data.sh [OPTIONS]

Populate MockServer with realistic CRUD API example data for UI testing.

Options:
  --port PORT     MockServer port (default: 1080)
  --host HOST     MockServer host (default: localhost)
  -v, --verbose   Show detailed curl output
  --help          Show this help message

Examples:
  ./scripts/ui_dev_populate_data.sh
  ./scripts/ui_dev_populate_data.sh --port 9090 -v
EOF
}

while [[ $# -gt 0 ]]; do
  case $1 in
    --port)
      MOCKSERVER_PORT="$2"
      shift 2
      ;;
    --host)
      MOCKSERVER_HOST="$2"
      shift 2
      ;;
    -v|--verbose)
      VERBOSE=true
      shift
      ;;
    --help)
      show_help
      exit 0
      ;;
    *)
      echo "Unknown option: $1"
      echo "Use --help for usage information"
      exit 1
      ;;
  esac
done

BASE_URL="http://${MOCKSERVER_HOST}:${MOCKSERVER_PORT}"

call_api() {
  if [ "$VERBOSE" = true ]; then
    curl -v "$@"
  else
    curl -s "$@" > /dev/null 2>&1
  fi
}

if ! curl -s "${BASE_URL}/mockserver/status" > /dev/null 2>&1; then
  echo "ERROR: Cannot connect to MockServer at ${BASE_URL}"
  echo "Make sure MockServer is running first"
  exit 1
fi

echo "Loading example data into MockServer at ${BASE_URL}..."
echo ""

echo "  → Resetting MockServer..."
call_api -X PUT "${BASE_URL}/mockserver/reset"

echo "  → Creating active expectations..."

echo "    [1/9] GET /users - List all users"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "GET",
      "path": "/users"
    },
    "httpResponse": {
      "statusCode": 200,
      "headers": {
        "Content-Type": ["application/json"]
      },
      "body": {
        "users": [
          {"id": 1, "name": "Alice Smith", "email": "alice@example.com", "role": "admin"},
          {"id": 2, "name": "Bob Jones", "email": "bob@example.com", "role": "user"},
          {"id": 3, "name": "Charlie Brown", "email": "charlie@example.com", "role": "user"}
        ]
      }
    }
  }'

echo "    [2/9] GET /users/{id} - Get single user"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "GET",
      "path": "/users/[0-9]+"
    },
    "httpResponse": {
      "statusCode": 200,
      "headers": {
        "Content-Type": ["application/json"]
      },
      "body": {
        "id": 1,
        "name": "Alice Smith",
        "email": "alice@example.com",
        "role": "admin",
        "created": "2024-01-15T10:30:00Z"
      }
    }
  }'

echo "    [3/9] POST /users - Create user"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "POST",
      "path": "/users",
      "headers": {
        "Content-Type": ["application/json"]
      }
    },
    "httpResponse": {
      "statusCode": 201,
      "headers": {
        "Content-Type": ["application/json"],
        "Location": ["/users/4"]
      },
      "body": {
        "id": 4,
        "status": "created",
        "message": "User created successfully"
      }
    }
  }'

echo "    [4/9] PUT /users/{id} - Update user"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "PUT",
      "path": "/users/[0-9]+"
    },
    "httpResponse": {
      "statusCode": 200,
      "headers": {
        "Content-Type": ["application/json"]
      },
      "body": {
        "id": 1,
        "status": "updated"
      }
    }
  }'

echo "    [5/9] DELETE /users/{id} - Delete user"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "DELETE",
      "path": "/users/[0-9]+"
    },
    "httpResponse": {
      "statusCode": 204
    }
  }'

echo "    [6/9] GET /products - Slow endpoint with delay"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "GET",
      "path": "/products"
    },
    "httpResponse": {
      "statusCode": 200,
      "headers": {
        "Content-Type": ["application/json"]
      },
      "delay": {
        "timeUnit": "MILLISECONDS",
        "value": 500
      },
      "body": {
        "products": [
          {"id": 101, "name": "Widget", "price": 29.99, "stock": 150},
          {"id": 102, "name": "Gadget", "price": 49.99, "stock": 75}
        ]
      }
    }
  }'

echo "    [7/9] POST /auth/login - Authentication endpoint"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "POST",
      "path": "/auth/login",
      "headers": {
        "Content-Type": ["application/json"]
      }
    },
    "httpResponse": {
      "statusCode": 200,
      "headers": {
        "Content-Type": ["application/json"]
      },
      "body": {
        "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U",
        "expiresIn": 3600,
        "user": {
          "id": 1,
          "name": "Alice Smith"
        }
      }
    }
  }'

echo "    [8/9] GET /error - Error response"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "method": "GET",
      "path": "/error"
    },
    "httpResponse": {
      "statusCode": 500,
      "headers": {
        "Content-Type": ["application/json"]
      },
      "body": {
        "error": "Internal Server Error",
        "message": "Something went wrong on the server",
        "code": "ERR_INTERNAL"
      }
    }
  }'

echo "    [9/9] Forward /httpbin/* to httpbin.org"
call_api -X PUT "${BASE_URL}/mockserver/expectation" \
  -H "Content-Type: application/json" \
  -d '{
    "httpRequest": {
      "path": "/httpbin/.*"
    },
    "httpForward": {
      "host": "httpbin.org",
      "port": 443,
      "scheme": "HTTPS"
    }
  }'

echo ""
echo "  → Sending test requests (matched)..."
call_api "${BASE_URL}/users"
call_api "${BASE_URL}/users/1"
call_api "${BASE_URL}/users/2"
call_api "${BASE_URL}/users/3"
call_api -X POST "${BASE_URL}/users" -H "Content-Type: application/json" -d '{"name":"David Miller","email":"david@example.com","role":"user"}'
call_api -X PUT "${BASE_URL}/users/1" -H "Content-Type: application/json" -d '{"name":"Alice Smith Updated","email":"alice@example.com"}'
call_api -X DELETE "${BASE_URL}/users/3"
call_api "${BASE_URL}/products"
call_api -X POST "${BASE_URL}/auth/login" -H "Content-Type: application/json" -d '{"username":"admin","password":"secret123"}'
call_api "${BASE_URL}/error"

echo "  → Sending test requests (unmatched)..."
call_api "${BASE_URL}/unknown" || true
call_api "${BASE_URL}/api/v2/users" || true
call_api -X PATCH "${BASE_URL}/users/1" || true
call_api "${BASE_URL}/users/abc" || true
call_api -X POST "${BASE_URL}/products" -d '{"name":"test"}' || true

echo "  → Triggering proxied requests..."
call_api "${BASE_URL}/httpbin/get" || true
call_api "${BASE_URL}/httpbin/uuid" || true
call_api -X POST "${BASE_URL}/httpbin/post" -H "Content-Type: application/json" -d '{"test":"data","source":"mockserver-ui-dev"}' || true

echo ""
echo "========================================"
echo "✓ Example Data Loaded Successfully"
echo "========================================"
echo ""
echo "Active Expectations:  9"
echo "Requests Sent:        ~15"
echo "Proxied Requests:     ~3"
echo ""
echo "View in UI: ${BASE_URL}/mockserver/dashboard"
echo ""
