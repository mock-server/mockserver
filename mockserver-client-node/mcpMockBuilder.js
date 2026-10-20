/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

/*
 * Node/TypeScript port of the Java client McpMockBuilder
 * (org.mockserver.client.McpMockBuilder).
 *
 * Builds the set of MockServer expectations needed to mock an MCP
 * (Model Context Protocol) server speaking JSON-RPC 2.0 over the
 * Streamable HTTP transport.  The produced expectation JSON is wire
 * identical to the Java builder so both clients drive the same server
 * behaviour.
 *
 * Usage:
 *
 *   var mcpMock = require('mockserver-client').mcpMock;   // or client.mcpMock
 *
 *   mcpMock("/mcp")
 *       .withServerName("MyServer")
 *       .withTool("get_weather")
 *           .withDescription("Get the weather for a city")
 *           .withInputSchema('{"type":"object","properties":{"city":{"type":"string"}}}')
 *           .respondingWith("sunny")
 *       .and()
 *       .withResource("file:///config.json")
 *           .withName("config")
 *           .withMimeType("application/json")
 *           .withContent('{"debug":true}')
 *       .and()
 *       .applyTo(client);   // returns a promise (delegates to mockAnyResponse)
 *
 * Or, without a client, obtain the raw expectation array:
 *
 *   var expectations = mcpMock("/mcp"). ... .build();
 */

(function () {
    "use strict";

    // ------------------------------------------------------------------
    // Escaping helpers — ported 1:1 from the Java McpMockBuilder so the
    // produced template strings are byte-identical.
    // ------------------------------------------------------------------

    /**
     * JSON-escape a string for inlining inside a JSON string literal,
     * returning the contents WITHOUT the surrounding quotes.  Mirrors
     * Java's OBJECT_MAPPER.writeValueAsString(value) then stripping the
     * outer quotes.
     */
    var escapeJson = function (value) {
        if (value === null || value === undefined) {
            return "";
        }
        var quoted = JSON.stringify(String(value));
        return quoted.substring(1, quoted.length - 1);
    };

    var velocityMetacharacterEscape = function (metacharacter) {
        if (metacharacter === "$") {
            return "${esc.d}";
        }
        if (metacharacter === "#") {
            return "${esc.h}";
        }
        return "";
    };

    /**
     * Escape Velocity meta-characters so literal '$' and '#' in mock
     * content are not interpreted as Velocity references/directives.
     * Velocity consumes backslashes written before a reference or directive,
     * so a run of backslashes followed by '$', '#' or the end of the value is
     * escaped too; other backslashes are left as-is. Mirrors Java.
     */
    var escapeVelocity = function (value) {
        if (value === null || value === undefined) {
            return value;
        }
        // group 2 consumes the metacharacter after a backslash run, is empty at
        // the end and unset (empty alternative) otherwise, so no lookaround is needed
        return String(value).replace(/(\\+)(?:([$#]|$)|)|[$#]/g, function (match, run, following) {
            if (run === undefined) {
                return velocityMetacharacterEscape(match);
            }
            if (following === undefined) {
                return run;
            }
            return "${esc.b}".repeat(run.length) + velocityMetacharacterEscape(following);
        });
    };

    /**
     * Validate that the supplied string is valid JSON and return it
     * re-serialised in compact form.  Mirrors Java's
     * validateAndSerializeJson (which round-trips through Jackson).
     */
    var validateAndSerializeJson = function (json) {
        try {
            return JSON.stringify(JSON.parse(json));
        } catch (e) {
            throw new Error("Invalid JSON for inputSchema: " + (e && e.message ? e.message : e));
        }
    };

    /**
     * Escape backslashes, then single quotes, for safe inclusion inside a
     * single-quoted JSONPath string literal.
     */
    var escapeJsonPath = function (value) {
        if (value === null || value === undefined) {
            return "";
        }
        return String(value).split("\\").join("\\\\").split("'").join("\\'");
    };

    /**
     * Build the Velocity template string that renders a JSON-RPC 2.0
     * success response wrapping the supplied result JSON.  The id is
     * echoed back from the inbound request via the jsonRpcRawId binding.
     * Mirrors Java's velocityJsonRpcResponse.
     */
    var velocityJsonRpcResponse = function (resultJson) {
        return "{\"statusCode\": 200, " +
            "\"headers\": [{\"name\": \"Content-Type\", \"values\": [\"application/json\"]}], " +
            "\"body\": {\"jsonrpc\": \"2.0\", \"result\": " + resultJson + ", \"id\": $!{request.jsonRpcRawId}}}";
    };

    // ------------------------------------------------------------------
    // Expectation fragment helpers
    // ------------------------------------------------------------------

    var jsonRpcRequest = function (path, method) {
        return {
            method: "POST",
            path: path,
            body: {
                type: "JSON_RPC",
                method: method
            }
        };
    };

    var jsonPathRequest = function (path, jsonPath) {
        return {
            method: "POST",
            path: path,
            body: {
                type: "JSON_PATH",
                jsonPath: jsonPath
            }
        };
    };

    var velocityTemplateResponseExpectation = function (httpRequest, resultJson) {
        return {
            httpRequest: httpRequest,
            httpResponseTemplate: {
                template: velocityJsonRpcResponse(resultJson),
                templateType: "VELOCITY"
            }
        };
    };

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    var mcpMock = function (path) {
        var state = {
            path: typeof path === "string" ? path : "/mcp",
            serverName: "MockMCPServer",
            serverVersion: "1.0.0",
            protocolVersion: "2025-03-26",
            toolsCapability: false,
            resourcesCapability: false,
            promptsCapability: false,
            tools: [],
            resources: [],
            prompts: []
        };

        // ---- initialize / ping / notifications ----

        var buildInitializeExpectation = function () {
            var caps = "{";
            var first = true;
            if (state.toolsCapability || state.tools.length > 0) {
                caps += "\"tools\": {\"listChanged\": false}";
                first = false;
            }
            if (state.resourcesCapability || state.resources.length > 0) {
                if (!first) {
                    caps += ", ";
                }
                caps += "\"resources\": {\"subscribe\": false, \"listChanged\": false}";
                first = false;
            }
            if (state.promptsCapability || state.prompts.length > 0) {
                if (!first) {
                    caps += ", ";
                }
                caps += "\"prompts\": {\"listChanged\": false}";
            }
            caps += "}";

            var resultJson = "{\"protocolVersion\": \"" + escapeVelocity(escapeJson(state.protocolVersion)) + "\", " +
                "\"capabilities\": " + caps + ", " +
                "\"serverInfo\": {\"name\": \"" + escapeVelocity(escapeJson(state.serverName)) + "\", \"version\": \"" + escapeVelocity(escapeJson(state.serverVersion)) + "\"}}";

            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "initialize"), resultJson);
        };

        var buildPingExpectation = function () {
            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "ping"), "{}");
        };

        var buildNotificationsInitializedExpectation = function () {
            return {
                httpRequest: jsonRpcRequest(state.path, "notifications/initialized"),
                httpResponse: {
                    statusCode: 200,
                    headers: [{name: "Content-Type", values: ["application/json"]}],
                    body: "{}"
                }
            };
        };

        // ---- tools ----

        var buildToolsListExpectation = function () {
            var toolsJson = "[";
            for (var i = 0; i < state.tools.length; i++) {
                if (i > 0) {
                    toolsJson += ", ";
                }
                var tool = state.tools[i];
                toolsJson += "{\"name\": \"" + escapeVelocity(escapeJson(tool.name)) + "\"";
                if (tool.description !== null && tool.description !== undefined) {
                    toolsJson += ", \"description\": \"" + escapeVelocity(escapeJson(tool.description)) + "\"";
                }
                if (tool.inputSchema !== null && tool.inputSchema !== undefined) {
                    toolsJson += ", \"inputSchema\": " + escapeVelocity(validateAndSerializeJson(tool.inputSchema));
                }
                toolsJson += "}";
            }
            toolsJson += "]";

            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "tools/list"), "{\"tools\": " + toolsJson + "}");
        };

        var buildToolsCallExpectation = function (tool) {
            var jsonPath = "$[?(@.method == 'tools/call' && @.params.name == '" + escapeJsonPath(tool.name) + "')]";
            var content = (tool.responseContent !== null && tool.responseContent !== undefined) ? escapeVelocity(escapeJson(tool.responseContent)) : "";
            var isError = tool.responseIsError ? "true" : "false";
            var resultJson = "{\"content\": [{\"type\": \"text\", \"text\": \"" + content + "\"}], \"isError\": " + isError + "}";

            return velocityTemplateResponseExpectation(jsonPathRequest(state.path, jsonPath), resultJson);
        };

        // ---- resources ----

        var buildResourcesListExpectation = function () {
            var resourcesJson = "[";
            for (var i = 0; i < state.resources.length; i++) {
                if (i > 0) {
                    resourcesJson += ", ";
                }
                var resource = state.resources[i];
                resourcesJson += "{\"uri\": \"" + escapeVelocity(escapeJson(resource.uri)) + "\"";
                if (resource.name !== null && resource.name !== undefined) {
                    resourcesJson += ", \"name\": \"" + escapeVelocity(escapeJson(resource.name)) + "\"";
                }
                if (resource.description !== null && resource.description !== undefined) {
                    resourcesJson += ", \"description\": \"" + escapeVelocity(escapeJson(resource.description)) + "\"";
                }
                if (resource.mimeType !== null && resource.mimeType !== undefined) {
                    resourcesJson += ", \"mimeType\": \"" + escapeVelocity(escapeJson(resource.mimeType)) + "\"";
                }
                resourcesJson += "}";
            }
            resourcesJson += "]";

            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "resources/list"), "{\"resources\": " + resourcesJson + "}");
        };

        var buildResourcesReadExpectation = function (resource) {
            var jsonPath = "$[?(@.method == 'resources/read' && @.params.uri == '" + escapeJsonPath(resource.uri) + "')]";
            var content = (resource.content !== null && resource.content !== undefined) ? escapeVelocity(escapeJson(resource.content)) : "";
            var mimeType = (resource.mimeType !== null && resource.mimeType !== undefined) ? resource.mimeType : "application/json";
            var resultJson = "{\"contents\": [{\"uri\": \"" + escapeVelocity(escapeJson(resource.uri)) + "\", " +
                "\"mimeType\": \"" + escapeVelocity(escapeJson(mimeType)) + "\", " +
                "\"text\": \"" + content + "\"}]}";

            return velocityTemplateResponseExpectation(jsonPathRequest(state.path, jsonPath), resultJson);
        };

        // ---- prompts ----

        var buildPromptsListExpectation = function () {
            var promptsJson = "[";
            for (var i = 0; i < state.prompts.length; i++) {
                if (i > 0) {
                    promptsJson += ", ";
                }
                var prompt = state.prompts[i];
                promptsJson += "{\"name\": \"" + escapeVelocity(escapeJson(prompt.name)) + "\"";
                if (prompt.description !== null && prompt.description !== undefined) {
                    promptsJson += ", \"description\": \"" + escapeVelocity(escapeJson(prompt.description)) + "\"";
                }
                if (prompt.arguments.length > 0) {
                    promptsJson += ", \"arguments\": [";
                    for (var j = 0; j < prompt.arguments.length; j++) {
                        if (j > 0) {
                            promptsJson += ", ";
                        }
                        var arg = prompt.arguments[j];
                        promptsJson += "{\"name\": \"" + escapeVelocity(escapeJson(arg.name)) + "\"";
                        if (arg.description !== null && arg.description !== undefined) {
                            promptsJson += ", \"description\": \"" + escapeVelocity(escapeJson(arg.description)) + "\"";
                        }
                        promptsJson += ", \"required\": " + (arg.required ? "true" : "false");
                        promptsJson += "}";
                    }
                    promptsJson += "]";
                }
                promptsJson += "}";
            }
            promptsJson += "]";

            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "prompts/list"), "{\"prompts\": " + promptsJson + "}");
        };

        var buildPromptsGetExpectation = function (prompt) {
            var jsonPath = "$[?(@.method == 'prompts/get' && @.params.name == '" + escapeJsonPath(prompt.name) + "')]";
            var messagesJson = "[";
            for (var i = 0; i < prompt.messages.length; i++) {
                if (i > 0) {
                    messagesJson += ", ";
                }
                var msg = prompt.messages[i];
                messagesJson += "{\"role\": \"" + escapeVelocity(escapeJson(msg.role)) + "\", ";
                messagesJson += "\"content\": {\"type\": \"text\", \"text\": \"" + escapeVelocity(escapeJson(msg.text)) + "\"}}";
            }
            messagesJson += "]";

            var resultJson = "{\"messages\": " + messagesJson + "}";

            return velocityTemplateResponseExpectation(jsonPathRequest(state.path, jsonPath), resultJson);
        };

        // ---- assembly ----

        var build = function () {
            var expectations = [];

            expectations.push(buildInitializeExpectation());
            expectations.push(buildPingExpectation());
            expectations.push(buildNotificationsInitializedExpectation());

            if (state.toolsCapability || state.tools.length > 0) {
                expectations.push(buildToolsListExpectation());
            }
            for (var t = 0; t < state.tools.length; t++) {
                expectations.push(buildToolsCallExpectation(state.tools[t]));
            }

            if (state.resourcesCapability || state.resources.length > 0) {
                expectations.push(buildResourcesListExpectation());
            }
            for (var r = 0; r < state.resources.length; r++) {
                expectations.push(buildResourcesReadExpectation(state.resources[r]));
            }

            if (state.promptsCapability || state.prompts.length > 0) {
                expectations.push(buildPromptsListExpectation());
            }
            for (var p = 0; p < state.prompts.length; p++) {
                expectations.push(buildPromptsGetExpectation(state.prompts[p]));
            }

            return expectations;
        };

        // ---- nested fluent builders ----

        // Forward declaration: the nested builders' .and() returns the root
        // builder, which is defined below.  Declared up front so the reference
        // resolves cleanly (and satisfies jshint's latedef).
        var rootApi;

        var toolBuilder = function (name) {
            var tool = {
                name: name,
                description: null,
                inputSchema: null,
                responseContent: null,
                responseIsError: false
            };
            var toolApi = {
                withDescription: function (description) {
                    tool.description = description;
                    return toolApi;
                },
                withInputSchema: function (jsonSchema) {
                    tool.inputSchema = jsonSchema;
                    return toolApi;
                },
                respondingWith: function (textContent, isError) {
                    tool.responseContent = textContent;
                    if (isError !== undefined) {
                        tool.responseIsError = !!isError;
                    }
                    return toolApi;
                },
                and: function () {
                    state.tools.push(tool);
                    state.toolsCapability = true;
                    return rootApi;
                }
            };
            return toolApi;
        };

        var resourceBuilder = function (uri) {
            var resource = {
                uri: uri,
                name: null,
                description: null,
                mimeType: "application/json",
                content: null
            };
            var resourceApi = {
                withName: function (name) {
                    resource.name = name;
                    return resourceApi;
                },
                withDescription: function (description) {
                    resource.description = description;
                    return resourceApi;
                },
                withMimeType: function (mimeType) {
                    resource.mimeType = mimeType;
                    return resourceApi;
                },
                withContent: function (content) {
                    resource.content = content;
                    return resourceApi;
                },
                and: function () {
                    state.resources.push(resource);
                    state.resourcesCapability = true;
                    return rootApi;
                }
            };
            return resourceApi;
        };

        var promptBuilder = function (name) {
            var prompt = {
                name: name,
                description: null,
                arguments: [],
                messages: []
            };
            var promptApi = {
                withDescription: function (description) {
                    prompt.description = description;
                    return promptApi;
                },
                withArgument: function (argName, description, required) {
                    prompt.arguments.push({name: argName, description: description, required: !!required});
                    return promptApi;
                },
                respondingWith: function (role, textContent) {
                    prompt.messages.push({role: role, text: textContent});
                    return promptApi;
                },
                and: function () {
                    state.prompts.push(prompt);
                    state.promptsCapability = true;
                    return rootApi;
                }
            };
            return promptApi;
        };

        rootApi = {
            withServerName: function (name) {
                state.serverName = name;
                return rootApi;
            },
            withServerVersion: function (version) {
                state.serverVersion = version;
                return rootApi;
            },
            withProtocolVersion: function (version) {
                state.protocolVersion = version;
                return rootApi;
            },
            withToolsCapability: function () {
                state.toolsCapability = true;
                return rootApi;
            },
            withResourcesCapability: function () {
                state.resourcesCapability = true;
                return rootApi;
            },
            withPromptsCapability: function () {
                state.promptsCapability = true;
                return rootApi;
            },
            withTool: function (name) {
                return toolBuilder(name);
            },
            withResource: function (uri) {
                return resourceBuilder(uri);
            },
            withPrompt: function (name) {
                return promptBuilder(name);
            },
            build: build,
            applyTo: function (client) {
                return client.mockAnyResponse(build());
            }
        };

        return rootApi;
    };

    if (typeof module !== 'undefined') {
        module.exports = {
            mcpMock: mcpMock
        };
    }
})();
