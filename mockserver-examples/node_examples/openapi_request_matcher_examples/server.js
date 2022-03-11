function matchRequestByOpenAPILoadedByHttpUrl() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOpenAPIOperation() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json",
            "operationId": "showPetById"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOpenAPILoadedByFileUrl() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "specUrlOrPayload": "file:/Users/jamesbloom/git/mockserver/mockserver/mockserver-core/target/test-classes/org/mockserver/openapi/openapi_petstore_example.json"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOpenAPILoadedByClasspathLocation() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "specUrlOrPayload": "org/mockserver/openapi/openapi_petstore_example.json"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOpenAPILoadedByJsonStringLiteral() {
    var fs = require('fs');
    var jsEscape = require('js-string-escape');
    try {
        var mockServerClient = require('mockserver-client').mockServerClient;
        mockServerClient("localhost", 1080).mockAnyResponse({
            "httpRequest": {
                "specUrlOrPayload": jsEscape.jsStringEscape(fs.readFileSync("/Users/jamesbloom/git/mockserver/mockserver/mockserver-core/target/test-classes/org/mockserver/openapi/openapi_petstore_example.json", "utf8")),
                "operationId": "showPetById"
            },
            "httpResponse": {
                "body": "some_response_body"
            }
        }).then(
            function () {
                console.log("expectation created");
            },
            function (error) {
                console.log(error);
            }
        );
    } catch (e) {
        console.log('Error:', e.stack);
    }
}

function matchRequestByOpenAPILoadedByYamlStringLiteral() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "specUrlOrPayload": "---\n" +
                "openapi: 3.0.0\n" +
                "info:\n" +
                "  version: 1.0.0\n" +
                "  title: Swagger Petstore\n" +
                "  license:\n" +
                "    name: MIT\n" +
                "servers:\n" +
                "  - url: http://petstore.swagger.io/v1\n" +
                "paths:\n" +
                "  /pets:\n" +
                "    get:\n" +
                "      summary: List all pets\n" +
                "      operationId: listPets\n" +
                "      tags:\n" +
                "        - pets\n" +
                "      parameters:\n" +
                "        - name: limit\n" +
                "          in: query\n" +
                "          description: How many items to return at one time (max 100)\n" +
                "          required: false\n" +
                "          schema:\n" +
                "            type: integer\n" +
                "            format: int32\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: A paged array of pets\n" +
                "          headers:\n" +
                "            x-next:\n" +
                "              description: A link to the next page of responses\n" +
                "              schema:\n" +
                "                type: string\n" +
                "              examples:\n" +
                "                two:\n" +
                "                  value: \"/pets?query=752cd724e0d7&page=2\"\n" +
                "                end:\n" +
                "                  value: \"\"\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Pets'\n" +
                "        '500':\n" +
                "          description: unexpected error\n" +
                "          headers:\n" +
                "            x-code:\n" +
                "              description: The error code\n" +
                "              schema:\n" +
                "                type: integer\n" +
                "                format: int32\n" +
                "                example: 90\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "        default:\n" +
                "          description: unexpected error\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "    post:\n" +
                "      summary: Create a pet\n" +
                "      operationId: createPets\n" +
                "      tags:\n" +
                "        - pets\n" +
                "      requestBody:\n" +
                "        description: a pet\n" +
                "        required: true\n" +
                "        content:\n" +
                "          application/json:\n" +
                "            schema:\n" +
                "              $ref: '#/components/schemas/Pet'\n" +
                "          '*/*':\n" +
                "            schema:\n" +
                "              $ref: '#/components/schemas/Pet'\n" +
                "      responses:\n" +
                "        '201':\n" +
                "          description: Null response\n" +
                "        '400':\n" +
                "          description: unexpected error\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "        '500':\n" +
                "          description: unexpected error\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "        default:\n" +
                "          description: unexpected error\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "  /pets/{petId}:\n" +
                "    get:\n" +
                "      summary: Info for a specific pet\n" +
                "      operationId: showPetById\n" +
                "      tags:\n" +
                "        - pets\n" +
                "      parameters:\n" +
                "        - name: petId\n" +
                "          in: path\n" +
                "          required: true\n" +
                "          description: The id of the pet to retrieve\n" +
                "          schema:\n" +
                "            type: string\n" +
                "        - in: header\n" +
                "          name: X-Request-ID\n" +
                "          schema:\n" +
                "            type: string\n" +
                "            format: uuid\n" +
                "          required: true\n" +
                "      responses:\n" +
                "        '200':\n" +
                "          description: Expected response to a valid request\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Pet'\n" +
                "              examples:\n" +
                "                Crumble:\n" +
                "                  value:\n" +
                "                    id: 2\n" +
                "                    name: Crumble\n" +
                "                    tag: dog\n" +
                "                Boots:\n" +
                "                  value:\n" +
                "                    id: 3\n" +
                "                    name: Boots\n" +
                "                    tag: cat\n" +
                "        '500':\n" +
                "          description: unexpected error\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "        default:\n" +
                "          description: unexpected error\n" +
                "          content:\n" +
                "            application/json:\n" +
                "              schema:\n" +
                "                $ref: '#/components/schemas/Error'\n" +
                "components:\n" +
                "  schemas:\n" +
                "    Pet:\n" +
                "      type: object\n" +
                "      required:\n" +
                "        - id\n" +
                "        - name\n" +
                "      properties:\n" +
                "        id:\n" +
                "          type: integer\n" +
                "          format: int64\n" +
                "        name:\n" +
                "          type: string\n" +
                "        tag:\n" +
                "          type: string\n" +
                "      example:\n" +
                "        id: 1\n" +
                "        name: Scruffles\n" +
                "        tag: dog\n" +
                "    Pets:\n" +
                "      type: array\n" +
                "      items:\n" +
                "        $ref: '#/components/schemas/Pet'\n" +
                "    Error:\n" +
                "      type: object\n" +
                "      required:\n" +
                "        - code\n" +
                "        - message\n" +
                "      properties:\n" +
                "        code:\n" +
                "          type: integer\n" +
                "          format: int32\n" +
                "        message:\n" +
                "          type: string\n",
            "operationId": "listPets"
        },
        "httpResponse": {
            "body": "some_response_body"
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function matchRequestByOpenAPIOperationTwice() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "httpRequest": {
            "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json",
            "operationId": "showPetById"
        },
        "httpResponse": {
            "statusCode": 200,
            "body": "some_body"
        },
        "times": {
            "unlimited": true
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}

function updateExpectationById() {
    var mockServerClient = require('mockserver-client').mockServerClient;
    mockServerClient("localhost", 1080).mockAnyResponse({
        "id": "630a6e5b-9d61-4668-a18f-a0d3df558583",
        "priority": 0,
        "httpRequest": {
            "specUrlOrPayload": "https://raw.githubusercontent.com/mock-server/mockserver/master/mockserver-integration-testing/src/main/resources/org/mockserver/openapi/openapi_petstore_example.json",
            "operationId": "showPetById"
        },
        "httpResponse": {
            "statusCode": 200,
            "body": "some_response_body"
        },
        "times": {
            "unlimited": true
        },
        "timeToLive": {
            "unlimited": true
        }
    }).then(
        function () {
            console.log("expectation created");
        },
        function (error) {
            console.log(error);
        }
    );
}
