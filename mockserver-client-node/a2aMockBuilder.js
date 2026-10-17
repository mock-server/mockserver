/*
 * mockserver
 * http://mock-server.com
 *
 * Copyright (c) 2014 James Bloom
 * Licensed under the Apache License, Version 2.0
 */

/*
 * Node/TypeScript port of the Java client A2aMockBuilder
 * (org.mockserver.client.A2aMockBuilder).
 *
 * Builds the set of MockServer expectations needed to mock an A2A
 * (Agent-to-Agent) agent: a static agent-card document served over GET, and
 * JSON-RPC 2.0 task methods (tasks/send, tasks/get, tasks/cancel) over POST,
 * with optional SSE streaming and push-notification config + delivery. The
 * produced expectation JSON is wire identical to the Java builder so both
 * clients drive the same server behaviour.
 *
 * Usage:
 *
 *   var a2aMock = require('mockserver-client').a2aMock;   // or client.a2aMock
 *
 *   a2aMock("/a2a")
 *       .withAgentName("MyAgent")
 *       .withSkill("translate")
 *           .withName("Translation")
 *           .withDescription("Translates text between languages")
 *           .withTag("i18n")
 *           .withExample("Translate hello to Spanish")
 *       .and()
 *       .withStreaming()
 *       .onTaskSend()
 *           .matchingMessage("translate.*")
 *           .respondingWith("Hola")
 *       .and()
 *       .applyTo(client);   // returns a promise (delegates to mockAnyResponse)
 *
 * Or, without a client, obtain the raw expectation array:
 *
 *   var expectations = a2aMock("/a2a"). ... .build();
 */

(function () {
    "use strict";

    // ------------------------------------------------------------------
    // Escaping helpers — ported 1:1 from the Java A2aMockBuilder so the
    // produced template/body strings are byte-identical.
    // ------------------------------------------------------------------

    /**
     * JSON-escape a string for inlining inside a JSON string literal,
     * returning the contents WITHOUT the surrounding quotes. Mirrors Java's
     * OBJECT_MAPPER.writeValueAsString(value) then stripping the outer quotes.
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
     * Escape Velocity meta-characters so literal '$' and '#' in mock content
     * are not interpreted as Velocity references/directives. Velocity consumes
     * backslashes written before a reference or directive, so a run of
     * backslashes followed by '$', '#' or the end of the value is escaped too;
     * other backslashes are left as-is. Mirrors Java.
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
     * Build the Velocity template string that renders a JSON-RPC 2.0 success
     * response wrapping the supplied result JSON. The id is echoed back from
     * the inbound request via the jsonRpcRawId binding. Mirrors Java's
     * velocityJsonRpcResponse.
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

    /**
     * Parse a webhook URL into {host, port, secure, path} — mirrors Java's
     * WebhookTarget.parse. Falls back to 80/443 by scheme when no explicit
     * port is given, and to "/" when the URL carries no path.
     */
    var parseWebhookTarget = function (webhookUrl) {
        // Node's URL is available in modern Node and in browsers; use it when
        // present, otherwise fall back to a tolerant regex parse so the builder
        // stays pure and dependency-free.
        var host, port, secure, path;
        try {
            var parsed = new URL(webhookUrl);
            secure = parsed.protocol === "https:";
            host = parsed.hostname;
            port = parsed.port ? parseInt(parsed.port, 10) : (secure ? 443 : 80);
            path = parsed.pathname && parsed.pathname.length > 0 ? parsed.pathname : "/";
        } catch (e) {
            var m = /^(https?):\/\/([^/:]+)(?::(\d+))?(\/[^?#]*)?/i.exec(webhookUrl || "");
            if (!m || !m[2]) {
                throw new Error("Invalid push-notification webhook URL (no host): " + webhookUrl);
            }
            secure = m[1].toLowerCase() === "https";
            host = m[2];
            port = m[3] ? parseInt(m[3], 10) : (secure ? 443 : 80);
            path = m[4] && m[4].length > 0 ? m[4] : "/";
        }
        if (!host) {
            throw new Error("Invalid push-notification webhook URL (no host): " + webhookUrl);
        }
        return {host: host, port: port, secure: secure, path: path};
    };

    // ------------------------------------------------------------------
    // Builder
    // ------------------------------------------------------------------

    var a2aMock = function (path) {
        var state = {
            path: typeof path === "string" ? path : "/a2a",
            agentCardPath: "/.well-known/agent.json",
            agentName: "MockAgent",
            agentDescription: "A mock A2A agent",
            agentVersion: "1.0.0",
            agentUrl: null,
            defaultTaskResponse: "Task completed successfully",
            streaming: false,
            streamingMethod: "message/stream",
            pushNotificationUrl: null,
            skills: [],
            taskHandlers: []
        };

        // ---- result-JSON helpers (mirror Java taskResultJson / buildTaskResultJson) ----

        var taskResultJson = function (escapedText, isError) {
            var taskState = isError ? "failed" : "completed";
            return "{\"id\": \"mock-task-id\", " +
                "\"status\": {\"state\": \"" + taskState + "\"}, " +
                "\"artifacts\": [{\"parts\": [{\"type\": \"text\", \"text\": \"" + escapedText + "\"}]}]}";
        };

        // For Velocity-templated response bodies: the text must survive the
        // Velocity engine, so metacharacters are escaped here and un-escaped by
        // the engine at response time.
        var buildTaskResultJson = function (responseText, isError) {
            return taskResultJson(escapeVelocity(escapeJson(responseText)), isError);
        };

        // For literal (non-templated) bodies (the webhook POST payload), where
        // no Velocity engine runs, only JSON escaping is applied — Velocity
        // escaping would corrupt any '$' / '#'.
        var buildTaskResultJsonRaw = function (responseText, isError) {
            return taskResultJson(escapeJson(responseText), isError);
        };

        // ---- agent card ----

        var buildAgentCardExpectation = function () {
            var skillsJson = "[";
            for (var i = 0; i < state.skills.length; i++) {
                if (i > 0) {
                    skillsJson += ", ";
                }
                var skill = state.skills[i];
                skillsJson += "{";
                skillsJson += "\"id\": \"" + escapeJson(skill.id) + "\"";
                skillsJson += ", \"name\": \"" + escapeJson(skill.name !== null && skill.name !== undefined ? skill.name : skill.id) + "\"";
                if (skill.description !== null && skill.description !== undefined) {
                    skillsJson += ", \"description\": \"" + escapeJson(skill.description) + "\"";
                }
                if (skill.tags.length > 0) {
                    skillsJson += ", \"tags\": [";
                    for (var t = 0; t < skill.tags.length; t++) {
                        if (t > 0) {
                            skillsJson += ", ";
                        }
                        skillsJson += "\"" + escapeJson(skill.tags[t]) + "\"";
                    }
                    skillsJson += "]";
                }
                if (skill.examples.length > 0) {
                    skillsJson += ", \"examples\": [";
                    for (var x = 0; x < skill.examples.length; x++) {
                        if (x > 0) {
                            skillsJson += ", ";
                        }
                        skillsJson += "\"" + escapeJson(skill.examples[x]) + "\"";
                    }
                    skillsJson += "]";
                }
                skillsJson += "}";
            }
            skillsJson += "]";

            var url = (state.agentUrl !== null && state.agentUrl !== undefined) ?
                state.agentUrl :
                "http://localhost" + state.path;

            var agentCardJson = "{" +
                "\"name\": \"" + escapeJson(state.agentName) + "\", " +
                "\"description\": \"" + escapeJson(state.agentDescription) + "\", " +
                "\"version\": \"" + escapeJson(state.agentVersion) + "\", " +
                "\"url\": \"" + escapeJson(url) + "\", " +
                "\"capabilities\": {\"streaming\": " + (state.streaming ? "true" : "false") +
                ", \"pushNotifications\": " + (state.pushNotificationUrl !== null ? "true" : "false") +
                ", \"stateTransitionHistory\": false}, " +
                "\"skills\": " + skillsJson + "}";

            return {
                httpRequest: {
                    method: "GET",
                    path: state.agentCardPath
                },
                httpResponse: {
                    statusCode: 200,
                    headers: [{name: "Content-Type", values: ["application/json"]}],
                    body: agentCardJson
                }
            };
        };

        // ---- task methods ----

        var buildTasksSendExpectation = function () {
            var resultJson = buildTaskResultJson(state.defaultTaskResponse, false);
            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "tasks/send"), resultJson);
        };

        var buildTasksGetExpectation = function () {
            var resultJson = buildTaskResultJson(state.defaultTaskResponse, false);
            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "tasks/get"), resultJson);
        };

        var buildTasksCancelExpectation = function () {
            var resultJson = "{\"id\": \"mock-task-id\", \"status\": {\"state\": \"canceled\"}}";
            return velocityTemplateResponseExpectation(jsonRpcRequest(state.path, "tasks/cancel"), resultJson);
        };

        // Escapes a user-supplied regular expression so it can be embedded
        // between the "/.../" delimiters of the JSON_PATH regex literal without
        // breaking out of it. Single left-to-right pass that PRESERVES existing
        // regex escape sequences (e.g. "\d", "\/") and only neutralizes the
        // delimiter ("/" -> "\/"), control characters (newline/CR), and a
        // trailing lone backslash (which would otherwise escape the closing
        // "/" delimiter). NUL is stripped.
        var escapeMessagePattern = function (pattern) {
            var out = "", i = 0, n = pattern.length;
            while (i < n) {
                var c = pattern.charAt(i);
                if (c === "\\") {
                    if (i + 1 < n) {
                        out += "\\" + pattern.charAt(i + 1);
                        i += 2;
                        continue;
                    }
                    out += "\\\\"; // trailing lone backslash -> literal backslash
                } else if (c === "/") {
                    out += "\\/";
                } else if (c === "\n") {
                    out += "\\n";
                } else if (c === "\r") {
                    out += "\\r";
                } else if (c === "\0") {
                    // strip NUL
                } else {
                    out += c;
                }
                i++;
            }
            return out;
        };

        var buildCustomTaskHandler = function (handler) {
            var escapedPattern = escapeMessagePattern(handler.messagePattern);
            var jsonPath = "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /" + escapedPattern + "/)]";
            var resultJson = buildTaskResultJson(handler.responseText, handler.isError);
            return velocityTemplateResponseExpectation(jsonPathRequest(state.path, jsonPath), resultJson);
        };

        // ---- streaming ----

        var buildStreamingExpectation = function () {
            var text = escapeJson(state.defaultTaskResponse);
            var taskId = "mock-task-id";

            // A2A streaming: each SSE event data is a JSON-RPC 2.0 response
            // envelope wrapping a TaskStatusUpdateEvent or
            // TaskArtifactUpdateEvent. The JSON-RPC id is not known at build
            // time, so a stable placeholder is used.
            var statusWorking = {
                event: "message",
                data: "{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                    "{\"taskId\": \"" + taskId + "\", \"kind\": \"status-update\", " +
                    "\"status\": {\"state\": \"working\"}, \"final\": false}}"
            };
            var artifactUpdate = {
                event: "message",
                data: "{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                    "{\"taskId\": \"" + taskId + "\", \"kind\": \"artifact-update\", " +
                    "\"artifact\": {\"parts\": [{\"type\": \"text\", \"text\": \"" + text + "\"}]}}}"
            };
            var statusCompleted = {
                event: "message",
                data: "{\"jsonrpc\": \"2.0\", \"id\": \"1\", \"result\": " +
                    "{\"taskId\": \"" + taskId + "\", \"kind\": \"status-update\", " +
                    "\"status\": {\"state\": \"completed\"}, \"final\": true}}"
            };

            return {
                httpRequest: jsonRpcRequest(state.path, state.streamingMethod),
                httpSseResponse: {
                    statusCode: 200,
                    events: [statusWorking, artifactUpdate, statusCompleted],
                    closeConnection: true
                }
            };
        };

        // ---- push notifications ----

        var buildPushNotificationConfigExpectation = function () {
            // Echo the registered push-notification config back as the JSON-RPC result.
            var resultJson = "{\"url\": \"" + escapeVelocity(escapeJson(state.pushNotificationUrl)) + "\"}";
            return velocityTemplateResponseExpectation(
                jsonRpcRequest(state.path, "tasks/pushNotificationConfig/set"),
                resultJson
            );
        };

        var buildPushNotificationDeliveryExpectation = function () {
            // When push notifications are configured, a tasks/send both returns
            // the JSON-RPC task response to the caller AND POSTs the completed
            // task to the configured webhook URL. Modelled with an
            // override-forwarded-request: the request override targets the
            // webhook (literal body), and a Velocity response template produces
            // the caller's JSON-RPC response so the request's id is echoed back.
            var target = parseWebhookTarget(state.pushNotificationUrl);

            // Literal webhook POST body — no Velocity engine runs over a request
            // override, so only JSON escaping is applied.
            var pushBody = "{\"jsonrpc\": \"2.0\", \"result\": " +
                buildTaskResultJsonRaw(state.defaultTaskResponse, false) + "}";

            var webhookRequest = {
                method: "POST",
                path: target.path,
                socketAddress: {
                    host: target.host,
                    port: target.port,
                    scheme: target.secure ? "HTTPS" : "HTTP"
                },
                secure: target.secure,
                headers: [
                    {name: "Host", values: [target.host + ":" + target.port]},
                    {name: "Content-Type", values: ["application/json"]}
                ],
                body: pushBody
            };

            return {
                httpRequest: jsonRpcRequest(state.path, "tasks/send"),
                httpOverrideForwardedRequest: {
                    requestOverride: webhookRequest,
                    responseTemplate: {
                        template: velocityJsonRpcResponse(buildTaskResultJson(state.defaultTaskResponse, false)),
                        templateType: "VELOCITY"
                    }
                }
            };
        };

        // ---- assembly (mirrors Java A2aMockBuilder.build ordering) ----

        var build = function () {
            var expectations = [];

            expectations.push(buildAgentCardExpectation());

            for (var h = 0; h < state.taskHandlers.length; h++) {
                expectations.push(buildCustomTaskHandler(state.taskHandlers[h]));
            }

            if (state.streaming) {
                expectations.push(buildStreamingExpectation());
            }

            if (state.pushNotificationUrl !== null && state.pushNotificationUrl !== undefined) {
                expectations.push(buildPushNotificationConfigExpectation());
                expectations.push(buildPushNotificationDeliveryExpectation());
            } else {
                expectations.push(buildTasksSendExpectation());
            }
            expectations.push(buildTasksGetExpectation());
            expectations.push(buildTasksCancelExpectation());

            return expectations;
        };

        // ---- nested fluent builders ----

        var rootApi;

        var skillBuilder = function (id) {
            var skill = {
                id: id,
                name: null,
                description: null,
                tags: [],
                examples: []
            };
            var skillApi = {
                withName: function (name) {
                    skill.name = name;
                    return skillApi;
                },
                withDescription: function (description) {
                    skill.description = description;
                    return skillApi;
                },
                withTag: function (tag) {
                    skill.tags.push(tag);
                    return skillApi;
                },
                withExample: function (example) {
                    skill.examples.push(example);
                    return skillApi;
                },
                and: function () {
                    state.skills.push(skill);
                    return rootApi;
                }
            };
            return skillApi;
        };

        var taskHandlerBuilder = function () {
            var handler = {
                messagePattern: ".*",
                responseText: "Task completed",
                isError: false
            };
            var handlerApi = {
                matchingMessage: function (pattern) {
                    handler.messagePattern = pattern;
                    return handlerApi;
                },
                respondingWith: function (text, isError) {
                    handler.responseText = text;
                    if (isError !== undefined) {
                        handler.isError = !!isError;
                    }
                    return handlerApi;
                },
                and: function () {
                    state.taskHandlers.push(handler);
                    return rootApi;
                }
            };
            return handlerApi;
        };

        rootApi = {
            withAgentName: function (name) {
                state.agentName = name;
                return rootApi;
            },
            withAgentDescription: function (description) {
                state.agentDescription = description;
                return rootApi;
            },
            withAgentVersion: function (version) {
                state.agentVersion = version;
                return rootApi;
            },
            withAgentUrl: function (url) {
                state.agentUrl = url;
                return rootApi;
            },
            withAgentCardPath: function (cardPath) {
                state.agentCardPath = cardPath;
                return rootApi;
            },
            withDefaultTaskResponse: function (response) {
                state.defaultTaskResponse = response;
                return rootApi;
            },
            withStreaming: function () {
                state.streaming = true;
                return rootApi;
            },
            withStreamingMethod: function (method) {
                state.streamingMethod = method;
                state.streaming = true;
                return rootApi;
            },
            withPushNotifications: function (webhookUrl) {
                state.pushNotificationUrl = webhookUrl;
                return rootApi;
            },
            withSkill: function (id) {
                return skillBuilder(id);
            },
            onTaskSend: function () {
                return taskHandlerBuilder();
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
            a2aMock: a2aMock
        };
    }
})();
