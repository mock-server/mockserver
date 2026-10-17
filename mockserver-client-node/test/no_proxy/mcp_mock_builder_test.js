'use strict';

/*
 * Pure unit tests for the MCP (Model Context Protocol) mock builder.
 *
 * These assert the exact expectation JSON produced by mcpMock(...).build(),
 * proving the Node client emits the same wire format as the Java client's
 * McpMockBuilder (JSON_RPC / JSON_PATH request matchers + a VELOCITY
 * httpResponseTemplate that renders a JSON-RPC 2.0 response echoing the
 * inbound id via $!{request.jsonRpcRawId}).
 *
 * No live server is required — the builder is a pure function.
 */

var { describe, it } = require('node:test');
var assert = require('node:assert/strict');

// Require the builder both directly and via the index/client surface so the
// public wiring is covered too.
var mcpMock = require('../../mcpMockBuilder').mcpMock;
var indexMcpMock = require('../../index').mcpMock;
var mockServerClient = require('../../index').mockServerClient;

// ---------- helpers ----------

/** Find the single expectation whose request body matches the given JSON_RPC method. */
function findByJsonRpc(expectations, method) {
    return expectations.filter(function (e) {
        return e.httpRequest && e.httpRequest.body &&
            e.httpRequest.body.type === 'JSON_RPC' && e.httpRequest.body.method === method;
    });
}

/** Find the single expectation whose request body is a JSON_PATH containing the substring. */
function findByJsonPath(expectations, substring) {
    return expectations.filter(function (e) {
        return e.httpRequest && e.httpRequest.body &&
            e.httpRequest.body.type === 'JSON_PATH' &&
            e.httpRequest.body.jsonPath.indexOf(substring) !== -1;
    });
}

// =========================================================================
// public wiring
// =========================================================================

describe('mcpMock wiring', function () {
    it('is exported from the package index', function () {
        assert.equal(typeof mcpMock, 'function');
        assert.equal(typeof indexMcpMock, 'function');
    });

    it('is exposed as a method on a client instance', function () {
        var client = mockServerClient('localhost', 1080);
        assert.equal(typeof client.mcpMock, 'function');
        var builder = client.mcpMock('/mcp');
        assert.equal(typeof builder.withTool, 'function');
        assert.equal(typeof builder.build, 'function');
        assert.equal(typeof builder.applyTo, 'function');
    });

    it('defaults the path to /mcp', function () {
        var expectations = mcpMock().build();
        expectations.forEach(function (e) {
            assert.equal(e.httpRequest.path, '/mcp');
        });
    });
});

// =========================================================================
// base protocol expectations (initialize / ping / notifications)
// =========================================================================

describe('mcpMock base protocol expectations', function () {
    it('always emits initialize, ping and notifications/initialized', function () {
        var expectations = mcpMock('/mcp').build();
        assert.equal(expectations.length, 3);
        assert.equal(findByJsonRpc(expectations, 'initialize').length, 1);
        assert.equal(findByJsonRpc(expectations, 'ping').length, 1);
        assert.equal(findByJsonRpc(expectations, 'notifications/initialized').length, 1);
    });

    it('renders the initialize result with serverInfo and an empty capabilities object', function () {
        var init = findByJsonRpc(mcpMock('/mcp').build(), 'initialize')[0];
        assert.deepEqual(init.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: { type: 'JSON_RPC', method: 'initialize' }
        });
        assert.equal(init.httpResponseTemplate.templateType, 'VELOCITY');
        assert.equal(
            init.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"protocolVersion": "2025-03-26", "capabilities": {}, ' +
            '"serverInfo": {"name": "MockMCPServer", "version": "1.0.0"}}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );
    });

    it('renders notifications/initialized as a plain 200 (not a template)', function () {
        var note = findByJsonRpc(mcpMock('/mcp').build(), 'notifications/initialized')[0];
        assert.equal(note.httpResponseTemplate, undefined);
        assert.deepEqual(note.httpResponse, {
            statusCode: 200,
            headers: [{ name: 'Content-Type', values: ['application/json'] }],
            body: '{}'
        });
    });

    it('honours withServerName / withServerVersion / withProtocolVersion', function () {
        var init = findByJsonRpc(
            mcpMock('/mcp').withServerName('S').withServerVersion('9').withProtocolVersion('2024-01-01').build(),
            'initialize'
        )[0];
        assert.ok(init.httpResponseTemplate.template.indexOf('"protocolVersion": "2024-01-01"') !== -1);
        assert.ok(init.httpResponseTemplate.template.indexOf('"name": "S", "version": "9"') !== -1);
    });
});

// =========================================================================
// tools
// =========================================================================

describe('mcpMock tool registration', function () {
    it('produces the exact tools/list and tools/call expectation JSON', function () {
        var expectations = mcpMock('/mcp')
            .withTool('get_weather')
            .withDescription('Get the weather for a city')
            .withInputSchema('{"type":"object","properties":{"city":{"type":"string"}}}')
            .respondingWith('sunny')
            .and()
            .build();

        // initialize, ping, notifications, tools/list, tools/call => 5
        assert.equal(expectations.length, 5);

        // ----- tools/list -----
        var list = findByJsonRpc(expectations, 'tools/list')[0];
        assert.deepEqual(list.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: { type: 'JSON_RPC', method: 'tools/list' }
        });
        assert.equal(list.httpResponseTemplate.templateType, 'VELOCITY');
        assert.equal(
            list.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"tools": [{"name": "get_weather", "description": "Get the weather for a city", ' +
            '"inputSchema": {"type":"object","properties":{"city":{"type":"string"}}}}]}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );

        // ----- tools/call -----
        var call = findByJsonPath(expectations, "@.method == 'tools/call'")[0];
        assert.deepEqual(call.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: {
                type: 'JSON_PATH',
                jsonPath: "$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]"
            }
        });
        assert.equal(call.httpResponseTemplate.templateType, 'VELOCITY');
        assert.equal(
            call.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"content": [{"type": "text", "text": "sunny"}], "isError": false}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );
    });

    it('flags an error response when respondingWith(text, true) is used', function () {
        var call = findByJsonPath(
            mcpMock('/mcp').withTool('boom').respondingWith('kaboom', true).and().build(),
            "@.method == 'tools/call'"
        )[0];
        assert.ok(call.httpResponseTemplate.template.indexOf('"isError": true') !== -1);
    });

    it('emits tools/list with no tools when only withToolsCapability() is set', function () {
        var expectations = mcpMock('/mcp').withToolsCapability().build();
        var list = findByJsonRpc(expectations, 'tools/list')[0];
        assert.ok(list.httpResponseTemplate.template.indexOf('"tools": []') !== -1);
        // initialize capabilities advertises tools
        var init = findByJsonRpc(expectations, 'initialize')[0];
        assert.ok(init.httpResponseTemplate.template.indexOf('"tools": {"listChanged": false}') !== -1);
    });
});

// =========================================================================
// resources
// =========================================================================

describe('mcpMock resource registration', function () {
    it('produces the exact resources/list and resources/read expectation JSON', function () {
        var expectations = mcpMock('/mcp')
            .withResource('file:///config.json')
            .withName('config')
            .withDescription('App config')
            .withMimeType('application/json')
            .withContent('{"debug":true}')
            .and()
            .build();

        // initialize, ping, notifications, resources/list, resources/read => 5
        assert.equal(expectations.length, 5);

        // ----- resources/list -----
        var list = findByJsonRpc(expectations, 'resources/list')[0];
        assert.deepEqual(list.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: { type: 'JSON_RPC', method: 'resources/list' }
        });
        assert.equal(
            list.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"resources": [{"uri": "file:///config.json", "name": "config", ' +
            '"description": "App config", "mimeType": "application/json"}]}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );

        // ----- resources/read -----
        var read = findByJsonPath(expectations, "@.method == 'resources/read'")[0];
        assert.deepEqual(read.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: {
                type: 'JSON_PATH',
                jsonPath: "$[?(@.method == 'resources/read' && @.params.uri == 'file:///config.json')]"
            }
        });
        assert.equal(
            read.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"contents": [{"uri": "file:///config.json", "mimeType": "application/json", ' +
            '"text": "{\\"debug\\":true}"}]}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );
    });

    it('defaults resource mimeType to application/json', function () {
        var read = findByJsonPath(
            mcpMock('/mcp').withResource('mem://x').withContent('hi').and().build(),
            "@.method == 'resources/read'"
        )[0];
        assert.ok(read.httpResponseTemplate.template.indexOf('"mimeType": "application/json"') !== -1);
    });
});

// =========================================================================
// escaping
// =========================================================================

describe('mcpMock escaping', function () {
    it('JSON-escapes and Velocity-escapes tool response content', function () {
        var call = findByJsonPath(
            mcpMock('/mcp').withTool('t').respondingWith('a$b#c"d').and().build(),
            "@.method == 'tools/call'"
        )[0];
        // " is JSON-escaped to \" ; $ -> ${esc.d} ; # -> ${esc.h}
        assert.ok(call.httpResponseTemplate.template.indexOf('a${esc.d}b${esc.h}c\\"d') !== -1);
    });

    it('Velocity-escapes backslashes only before $, # or the end of the value', function () {
        var call = findByJsonPath(
            mcpMock('/mcp').withTool('t').respondingWith('a\\$b c\\#d e\\nf end\\').and().build(),
            "@.method == 'tools/call'"
        )[0];
        // JSON-escaped first, so each source backslash is doubled; runs before $ / # / end are escaped
        var template = call.httpResponseTemplate.template;
        assert.ok(template.indexOf('a${esc.b}${esc.b}${esc.d}b') !== -1, template);
        assert.ok(template.indexOf('c${esc.b}${esc.b}${esc.h}d') !== -1, template);
        assert.ok(template.indexOf('e\\\\nf') !== -1, template);
        assert.ok(template.indexOf('end${esc.b}${esc.b}"') !== -1, template);
    });

    it('Velocity-escapes trailing, mixed and very long backslash runs', function () {
        // JSON-escaped first, so each source backslash is doubled
        var run = '\\'.repeat(100000);
        var cases = [
            ['end\\\\', 'end' + '${esc.b}'.repeat(4)],
            ['a\\b\\$c#', 'a\\\\b${esc.b}${esc.b}${esc.d}c${esc.h}'],
            [run + 'x', run + run + 'x'],
            [run + '$', '${esc.b}'.repeat(200000) + '${esc.d}'],
            [run, '${esc.b}'.repeat(200000)]
        ];
        cases.forEach(function (testCase) {
            var call = findByJsonPath(
                mcpMock('/mcp').withTool('t').respondingWith(testCase[0]).and().build(),
                "@.method == 'tools/call'"
            )[0];
            var template = call.httpResponseTemplate.template;
            assert.ok(template.indexOf('"text": "' + testCase[1] + '"') !== -1, testCase[0].slice(0, 20));
        });
    });

    it('matches the shared cross-client table through the builder', function () {
        // shared cross-client inputs; expected = escapeVelocity(escapeJson(input)), as the Java builder emits
        var cases = [
            ['a\\$b', 'a${esc.b}${esc.b}${esc.d}b'],
            ['a\\#b', 'a${esc.b}${esc.b}${esc.h}b'],
            ['end\\', 'end${esc.b}${esc.b}'],
            ['\\\\$', '${esc.b}${esc.b}${esc.b}${esc.b}${esc.d}'],
            ['${esc.b}', '${esc.d}{esc.b}'],
            ['é\\$ü', 'é${esc.b}${esc.b}${esc.d}ü'],
            ['\\\\x', '\\\\\\\\x'],
            ['$#', '${esc.d}${esc.h}']
        ];
        cases.forEach(function (testCase) {
            var call = findByJsonPath(
                mcpMock('/mcp').withTool('t').respondingWith(testCase[0]).and().build(),
                "@.method == 'tools/call'"
            )[0];
            var template = call.httpResponseTemplate.template;
            assert.ok(template.indexOf('"text": "' + testCase[1] + '"') !== -1, testCase[0] + ' -> ' + template);
        });
    });

    it('escapes single quotes inside the JSONPath matcher', function () {
        var call = mcpMock('/mcp').withTool("it's").respondingWith('x').and().build()
            .filter(function (e) { return e.httpRequest.body && e.httpRequest.body.type === 'JSON_PATH'; })[0];
        assert.ok(call.httpRequest.body.jsonPath.indexOf("it\\'s") !== -1);
    });

    it('rejects an invalid inputSchema JSON string', function () {
        assert.throws(function () {
            mcpMock('/mcp').withTool('t').withInputSchema('{not json').respondingWith('x').and().build();
        }, /Invalid JSON for inputSchema/);
    });
});

// =========================================================================
// prompts
// =========================================================================

describe('mcpMock prompt registration', function () {
    it('produces the exact prompts/list and prompts/get expectation JSON', function () {
        var expectations = mcpMock('/mcp')
            .withPrompt('greeting')
            .withDescription('A friendly greeting')
            .withArgument('name', 'the person to greet', true)
            .withArgument('mood', undefined, false)
            .respondingWith('user', 'Hello {{name}}!')
            .respondingWith('assistant', 'Hi there!')
            .and()
            .build();

        // initialize, ping, notifications, prompts/list, prompts/get => 5
        assert.equal(expectations.length, 5);

        // initialize advertises the prompts capability
        var init = findByJsonRpc(expectations, 'initialize')[0];
        assert.ok(init.httpResponseTemplate.template.indexOf('"prompts": {"listChanged": false}') !== -1);

        // ----- prompts/list -----
        var list = findByJsonRpc(expectations, 'prompts/list')[0];
        assert.deepEqual(list.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: { type: 'JSON_RPC', method: 'prompts/list' }
        });
        assert.equal(
            list.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"prompts": [{"name": "greeting", "description": "A friendly greeting", ' +
            '"arguments": [{"name": "name", "description": "the person to greet", "required": true}, ' +
            '{"name": "mood", "required": false}]}]}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );

        // ----- prompts/get -----
        var get = findByJsonPath(expectations, "@.method == 'prompts/get'")[0];
        assert.deepEqual(get.httpRequest, {
            method: 'POST',
            path: '/mcp',
            body: {
                type: 'JSON_PATH',
                jsonPath: "$[?(@.method == 'prompts/get' && @.params.name == 'greeting')]"
            }
        });
        assert.equal(
            get.httpResponseTemplate.template,
            '{"statusCode": 200, ' +
            '"headers": [{"name": "Content-Type", "values": ["application/json"]}], ' +
            '"body": {"jsonrpc": "2.0", "result": ' +
            '{"messages": [{"role": "user", "content": {"type": "text", "text": "Hello {{name}}!"}}, ' +
            '{"role": "assistant", "content": {"type": "text", "text": "Hi there!"}}]}' +
            ', "id": $!{request.jsonRpcRawId}}}'
        );
    });

    it('emits prompts/list with no prompts when only withPromptsCapability() is set', function () {
        var expectations = mcpMock('/mcp').withPromptsCapability().build();
        var list = findByJsonRpc(expectations, 'prompts/list')[0];
        assert.ok(list.httpResponseTemplate.template.indexOf('"prompts": []') !== -1);
        var init = findByJsonRpc(expectations, 'initialize')[0];
        assert.ok(init.httpResponseTemplate.template.indexOf('"prompts": {"listChanged": false}') !== -1);
    });

    it('omits the arguments array when a prompt has no arguments', function () {
        var list = findByJsonRpc(
            mcpMock('/mcp').withPrompt('p').respondingWith('user', 'hi').and().build(),
            'prompts/list'
        )[0];
        // a prompt with zero arguments renders just {"name": "p"} with no "arguments" key
        assert.ok(list.httpResponseTemplate.template.indexOf('{"prompts": [{"name": "p"}]}') !== -1);
    });
});

// =========================================================================
// capability toggles and multi-capability initialize
// =========================================================================

describe('mcpMock capability advertisement', function () {
    it('withResourcesCapability advertises resources and emits an empty resources/list', function () {
        var expectations = mcpMock('/mcp').withResourcesCapability().build();
        var init = findByJsonRpc(expectations, 'initialize')[0];
        assert.ok(init.httpResponseTemplate.template.indexOf('"resources": {"subscribe": false, "listChanged": false}') !== -1);
        var list = findByJsonRpc(expectations, 'resources/list')[0];
        assert.ok(list.httpResponseTemplate.template.indexOf('"resources": []') !== -1);
    });

    it('advertises all three capabilities together in the correct comma-joined order', function () {
        var init = findByJsonRpc(
            mcpMock('/mcp')
                .withToolsCapability()
                .withResourcesCapability()
                .withPromptsCapability()
                .build(),
            'initialize'
        )[0];
        assert.ok(init.httpResponseTemplate.template.indexOf(
            '"capabilities": {"tools": {"listChanged": false}, ' +
            '"resources": {"subscribe": false, "listChanged": false}, ' +
            '"prompts": {"listChanged": false}}'
        ) !== -1);
    });
});

// =========================================================================
// applyTo wiring
// =========================================================================

describe('mcpMock applyTo', function () {
    it('delegates to client.mockAnyResponse with the built expectation array', function () {
        var captured = null;
        var fakeClient = { mockAnyResponse: function (e) { captured = e; return 'APPLIED'; } };
        var result = mcpMock('/mcp')
            .withTool('t').respondingWith('x').and()
            .applyTo(fakeClient);
        assert.equal(result, 'APPLIED');
        assert.ok(Array.isArray(captured));
        assert.ok(captured.length >= 4);
    });
});
