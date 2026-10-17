<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use MockServer\A2a\A2aEscaping;
use MockServer\Llm\Role;
use MockServer\Mcp\McpEscaping;
use MockServer\Mcp\McpMockBuilder;
use PHPUnit\Framework\TestCase;

/**
 * Pure builder tests asserting the wire JSON shape produced by the MCP
 * builder, independent of any HTTP transport.
 */
class McpBuilderTest extends TestCase
{
    public function testBareServerProducesInitializePingNotifications(): void
    {
        $expectations = McpMockBuilder::mcpMock('/mcp')->build();

        // initialize + ping + notifications/initialized
        $this->assertCount(3, $expectations);

        $initialize = $expectations[0]->toArray();
        $this->assertSame('POST', $initialize['httpRequest']['method']);
        $this->assertSame('/mcp', $initialize['httpRequest']['path']);
        $this->assertSame(['type' => 'JSON_RPC', 'method' => 'initialize'], $initialize['httpRequest']['body']);
        $this->assertSame('VELOCITY', $initialize['httpResponseTemplate']['templateType']);

        $template = $initialize['httpResponseTemplate']['template'];
        $this->assertStringContainsString('$!{request.jsonRpcRawId}', $template);
        $this->assertStringContainsString('"protocolVersion": "2025-03-26"', $template);
        $this->assertStringContainsString('"name": "MockMCPServer"', $template);
        $this->assertStringContainsString('"capabilities": {}', $template);

        $ping = $expectations[1]->toArray();
        $this->assertSame(['type' => 'JSON_RPC', 'method' => 'ping'], $ping['httpRequest']['body']);
        $this->assertStringContainsString('"result": {}', $ping['httpResponseTemplate']['template']);

        $notifications = $expectations[2]->toArray();
        $this->assertSame(
            ['type' => 'JSON_RPC', 'method' => 'notifications/initialized'],
            $notifications['httpRequest']['body']
        );
        $this->assertSame(200, $notifications['httpResponse']['statusCode']);
        $this->assertSame('{}', $notifications['httpResponse']['body']);
        $this->assertSame(
            [['name' => 'Content-Type', 'values' => ['application/json']]],
            $notifications['httpResponse']['headers']
        );
    }

    public function testToolProducesListAndCallExpectations(): void
    {
        $expectations = McpMockBuilder::mcpMock('/mcp')
            ->withServerName('WeatherServer')
            ->withTool('get_weather')
                ->withDescription('Get weather for a city')
                ->withInputSchema('{"type": "object", "properties": {"city": {"type": "string"}}}')
                ->respondingWith('72F and sunny')
            ->and()
            ->build();

        // initialize, ping, notifications, tools/list, tools/call = 5
        $this->assertCount(5, $expectations);

        $initialize = $expectations[0]->toArray();
        $this->assertStringContainsString('"tools": {"listChanged": false}', $initialize['httpResponseTemplate']['template']);

        $toolsList = $expectations[3]->toArray();
        $this->assertSame(['type' => 'JSON_RPC', 'method' => 'tools/list'], $toolsList['httpRequest']['body']);
        $listTemplate = $toolsList['httpResponseTemplate']['template'];
        $this->assertStringContainsString('"name": "get_weather"', $listTemplate);
        $this->assertStringContainsString('"description": "Get weather for a city"', $listTemplate);
        // input schema is validated + compacted
        $this->assertStringContainsString('"inputSchema": {"type":"object","properties":{"city":{"type":"string"}}}', $listTemplate);

        $toolsCall = $expectations[4]->toArray();
        $this->assertSame('JSON_PATH', $toolsCall['httpRequest']['body']['type']);
        $this->assertSame(
            "$[?(@.method == 'tools/call' && @.params.name == 'get_weather')]",
            $toolsCall['httpRequest']['body']['jsonPath']
        );
        $callTemplate = $toolsCall['httpResponseTemplate']['template'];
        $this->assertStringContainsString('"text": "72F and sunny"', $callTemplate);
        $this->assertStringContainsString('"isError": false', $callTemplate);
    }

    public function testToolErrorFlag(): void
    {
        $expectations = McpMockBuilder::mcpMock()
            ->withTool('boom')->respondingWith('it failed', true)->and()
            ->build();

        $call = end($expectations)->toArray();
        $this->assertStringContainsString('"isError": true', $call['httpResponseTemplate']['template']);
    }

    public function testResourceProducesListAndReadExpectations(): void
    {
        $expectations = McpMockBuilder::mcpMock('/mcp')
            ->withResource('file:///config.json')
                ->withName('config')
                ->withMimeType('application/json')
                ->withContent('{"debug":true}')
            ->and()
            ->build();

        // initialize, ping, notifications, resources/list, resources/read = 5
        $this->assertCount(5, $expectations);

        $resourcesList = $expectations[3]->toArray();
        $this->assertSame('resources/list', $resourcesList['httpRequest']['body']['method']);
        $this->assertStringContainsString('"uri": "file:///config.json"', $resourcesList['httpResponseTemplate']['template']);
        $this->assertStringContainsString('"name": "config"', $resourcesList['httpResponseTemplate']['template']);

        $resourcesRead = $expectations[4]->toArray();
        $this->assertSame(
            "$[?(@.method == 'resources/read' && @.params.uri == 'file:///config.json')]",
            $resourcesRead['httpRequest']['body']['jsonPath']
        );
        $readTemplate = $resourcesRead['httpResponseTemplate']['template'];
        $this->assertStringContainsString('"mimeType": "application/json"', $readTemplate);
        // content is JSON-escaped for inlining in a string literal
        $this->assertStringContainsString('"text": "{\\"debug\\":true}"', $readTemplate);
    }

    public function testPromptProducesListAndGetExpectations(): void
    {
        $expectations = McpMockBuilder::mcpMock('/mcp')
            ->withPrompt('greeting')
                ->withDescription('Friendly greeting')
                ->withArgument('name', 'Who to greet', true)
                ->respondingWith(Role::ASSISTANT, 'Hello there!')
            ->and()
            ->build();

        // initialize, ping, notifications, prompts/list, prompts/get = 5
        $this->assertCount(5, $expectations);

        $promptsList = $expectations[3]->toArray();
        $this->assertSame('prompts/list', $promptsList['httpRequest']['body']['method']);
        $listTemplate = $promptsList['httpResponseTemplate']['template'];
        $this->assertStringContainsString('"name": "greeting"', $listTemplate);
        $this->assertStringContainsString('"arguments": [{"name": "name"', $listTemplate);
        $this->assertStringContainsString('"required": true', $listTemplate);

        $promptsGet = $expectations[4]->toArray();
        $this->assertSame(
            "$[?(@.method == 'prompts/get' && @.params.name == 'greeting')]",
            $promptsGet['httpRequest']['body']['jsonPath']
        );
        $getTemplate = $promptsGet['httpResponseTemplate']['template'];
        $this->assertStringContainsString('"role": "ASSISTANT"', $getTemplate);
        $this->assertStringContainsString('"text": "Hello there!"', $getTemplate);
    }

    public function testVelocityEscapingOfMetaCharacters(): void
    {
        $expectations = McpMockBuilder::mcpMock()
            ->withTool('echo')->respondingWith('cost is $5 #1')->and()
            ->build();

        $template = end($expectations)->toArray()['httpResponseTemplate']['template'];
        $this->assertStringContainsString('cost is ${esc.d}5 ${esc.h}1', $template);
    }

    public function testVelocityEscapingOfTrailingMixedAndVeryLongBackslashRuns(): void
    {
        $run = str_repeat('\\', 100000);
        $cases = [
            'end\\\\' => 'end${esc.b}${esc.b}',
            'a\\b\\$c#' => 'a\\b${esc.b}${esc.d}c${esc.h}',
            $run . 'x' => $run . 'x',
            $run . '$' => str_repeat('${esc.b}', 100000) . '${esc.d}',
            $run => str_repeat('${esc.b}', 100000),
        ];
        foreach ($cases as $input => $expected) {
            $this->assertSame($expected, McpEscaping::escapeVelocity((string) $input), 'input: ' . substr((string) $input, 0, 20));
            $this->assertSame($expected, A2aEscaping::escapeVelocity((string) $input), 'input: ' . substr((string) $input, 0, 20));
        }
    }

    /**
     * Shared cross-client table: backslash runs before $, # or the end of the
     * value are escaped; other backslashes are left as-is.
     */
    public function testVelocityEscapingOfBackslashesBeforeMetaCharacters(): void
    {
        $cases = [
            'a\\$b' => 'a${esc.b}${esc.d}b',
            'a\\#b' => 'a${esc.b}${esc.h}b',
            'end\\' => 'end${esc.b}',
            '\\\\$' => '${esc.b}${esc.b}${esc.d}',
            '${esc.b}' => '${esc.d}{esc.b}',
            'é\\$ü' => 'é${esc.b}${esc.d}ü',
            'a\\nb \\"q\\"' => 'a\\nb \\"q\\"',
            '\\\\x' => '\\\\x',
            '$#' => '${esc.d}${esc.h}',
            '' => '',
        ];
        foreach ($cases as $input => $expected) {
            $this->assertSame($expected, McpEscaping::escapeVelocity((string) $input), 'input: ' . $input);
            $this->assertSame($expected, A2aEscaping::escapeVelocity((string) $input), 'input: ' . $input);
        }

        $expectations = McpMockBuilder::mcpMock()
            ->withTool('echo')->respondingWith('a\\$b')->and()
            ->build();
        $template = end($expectations)->toArray()['httpResponseTemplate']['template'];
        // JSON-escaped "a\\$b": both backslashes precede '$', so all are escaped
        $this->assertStringContainsString('a${esc.b}${esc.b}${esc.d}b', $template);
    }

    public function testInvalidInputSchemaThrows(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        McpMockBuilder::mcpMock()
            ->withTool('bad')->withInputSchema('{not valid json')->and()
            ->build();
    }
}
