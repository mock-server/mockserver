<?php

declare(strict_types=1);

namespace MockServer\Tests\Unit;

use MockServer\A2a\A2aMockBuilder;
use PHPUnit\Framework\TestCase;

/**
 * Pure builder tests asserting the wire JSON shape produced by the A2A builder,
 * independent of any HTTP transport. The expected strings mirror the Java
 * {@code org.mockserver.client.A2aMockBuilder} contract.
 */
class A2aBuilderTest extends TestCase
{
    public function testBareAgentProducesAgentCardSendGetCancel(): void
    {
        $expectations = A2aMockBuilder::a2aMock('/a2a')->build();

        // agent-card + tasks/send + tasks/get + tasks/cancel
        $this->assertCount(4, $expectations);

        $agentCard = $expectations[0]->toArray();
        $this->assertSame('GET', $agentCard['httpRequest']['method']);
        $this->assertSame('/.well-known/agent.json', $agentCard['httpRequest']['path']);
        $this->assertSame(200, $agentCard['httpResponse']['statusCode']);
        $this->assertSame(
            [['name' => 'Content-Type', 'values' => ['application/json']]],
            $agentCard['httpResponse']['headers']
        );

        $card = $agentCard['httpResponse']['body'];
        $this->assertStringContainsString('"name": "MockAgent"', $card);
        $this->assertStringContainsString('"description": "A mock A2A agent"', $card);
        $this->assertStringContainsString('"version": "1.0.0"', $card);
        $this->assertStringContainsString('"url": "http://localhost/a2a"', $card);
        $this->assertStringContainsString(
            '"capabilities": {"streaming": false, "pushNotifications": false, "stateTransitionHistory": false}',
            $card
        );
        $this->assertStringContainsString('"skills": []', $card);
        // agent card body is literal JSON (no Velocity templating)
        $this->assertIsString($card);

        $send = $expectations[1]->toArray();
        $this->assertSame('POST', $send['httpRequest']['method']);
        $this->assertSame('/a2a', $send['httpRequest']['path']);
        $this->assertSame(['type' => 'JSON_RPC', 'method' => 'tasks/send'], $send['httpRequest']['body']);
        $this->assertSame('VELOCITY', $send['httpResponseTemplate']['templateType']);
        $sendTemplate = $send['httpResponseTemplate']['template'];
        $this->assertStringContainsString('$!{request.jsonRpcRawId}', $sendTemplate);
        $this->assertStringContainsString('"id": "mock-task-id"', $sendTemplate);
        $this->assertStringContainsString('"status": {"state": "completed"}', $sendTemplate);
        $this->assertStringContainsString('"text": "Task completed successfully"', $sendTemplate);

        $get = $expectations[2]->toArray();
        $this->assertSame(['type' => 'JSON_RPC', 'method' => 'tasks/get'], $get['httpRequest']['body']);

        $cancel = $expectations[3]->toArray();
        $this->assertSame(['type' => 'JSON_RPC', 'method' => 'tasks/cancel'], $cancel['httpRequest']['body']);
        $this->assertStringContainsString('"state": "canceled"', $cancel['httpResponseTemplate']['template']);
    }

    public function testSkillsRenderedInAgentCard(): void
    {
        $card = A2aMockBuilder::a2aMock('/a2a')
            ->withSkill('forecast')
                ->withName('Forecast')
                ->withDescription('Weather forecasting')
                ->withTag('weather')
                ->withTag('climate')
                ->withExample('What is the weather in London?')
            ->and()
            ->build()[0]
            ->toArray()['httpResponse']['body'];

        $this->assertStringContainsString('"id": "forecast"', $card);
        $this->assertStringContainsString('"name": "Forecast"', $card);
        $this->assertStringContainsString('"description": "Weather forecasting"', $card);
        $this->assertStringContainsString('"tags": ["weather", "climate"]', $card);
        $this->assertStringContainsString('"examples": ["What is the weather in London?"]', $card);
    }

    public function testSkillNameDefaultsToId(): void
    {
        $card = A2aMockBuilder::a2aMock()
            ->withSkill('echo')->and()
            ->build()[0]
            ->toArray()['httpResponse']['body'];

        $this->assertStringContainsString('"id": "echo", "name": "echo"', $card);
    }

    public function testCustomTaskHandlerProducesJsonPathMatcher(): void
    {
        $expectations = A2aMockBuilder::a2aMock('/a2a')
            ->onTaskSend()
                ->matchingMessage('.*weather.*')
                ->respondingWith('72F and sunny')
            ->and()
            ->build();

        // agent-card, custom handler, tasks/send, tasks/get, tasks/cancel
        $this->assertCount(5, $expectations);

        $handler = $expectations[1]->toArray();
        $this->assertSame('JSON_PATH', $handler['httpRequest']['body']['type']);
        $this->assertSame(
            "$[?(@.method == 'tasks/send' && @.params.message.parts[0].text =~ /.*weather.*/)]",
            $handler['httpRequest']['body']['jsonPath']
        );
        $this->assertStringContainsString('"text": "72F and sunny"', $handler['httpResponseTemplate']['template']);
        $this->assertStringContainsString('"state": "completed"', $handler['httpResponseTemplate']['template']);
    }

    public function testCustomTaskHandlerEscapesSlashesInPattern(): void
    {
        $handler = A2aMockBuilder::a2aMock()
            ->onTaskSend()->matchingMessage('a/b')->respondingWith('ok')->and()
            ->build()[1]
            ->toArray();

        $this->assertStringContainsString('a\\/b', $handler['httpRequest']['body']['jsonPath']);
    }

    public function testCustomTaskHandlerPreservesRegexEscapeSequences(): void
    {
        // '\d+' must stay '\d+' (single backslash), NOT be doubled to '\\d+'.
        $handler = A2aMockBuilder::a2aMock()
            ->onTaskSend()->matchingMessage('\\d+')->respondingWith('ok')->and()
            ->build()[1]
            ->toArray();

        $jsonPath = $handler['httpRequest']['body']['jsonPath'];
        $this->assertStringContainsString('=~ /\\d+/)]', $jsonPath);
        $this->assertStringNotContainsString('\\\\d', $jsonPath);
    }

    public function testCustomTaskHandlerDoesNotDoubleEscapedSlash(): void
    {
        // An already-escaped slash 'a\/b' must stay 'a\/b', NOT become 'a\\/b'.
        $handler = A2aMockBuilder::a2aMock()
            ->onTaskSend()->matchingMessage('a\\/b')->respondingWith('ok')->and()
            ->build()[1]
            ->toArray();

        $jsonPath = $handler['httpRequest']['body']['jsonPath'];
        $this->assertStringContainsString('a\\/b', $jsonPath);
        $this->assertStringNotContainsString('a\\\\/b', $jsonPath);
    }

    public function testCustomTaskHandlerTrailingBackslashCannotBreakOutOfRegex(): void
    {
        // A pattern ending in a lone backslash must not escape the closing '/'
        // delimiter of the regex literal — it is doubled to a literal backslash
        // and the jsonPath still ends with the UNESCAPED closing '/)]'.
        $handler = A2aMockBuilder::a2aMock()
            ->onTaskSend()->matchingMessage('weather\\')->respondingWith('ok')->and()
            ->build()[1]
            ->toArray();

        $jsonPath = $handler['httpRequest']['body']['jsonPath'];
        $this->assertStringContainsString('weather\\\\', $jsonPath);
        // Security assertion: the regex literal is still correctly terminated.
        $this->assertStringEndsWith('/)]', $jsonPath);
        $this->assertStringContainsString("=~ /weather\\\\/)]", $jsonPath);
    }

    public function testTaskHandlerErrorFlag(): void
    {
        $handler = A2aMockBuilder::a2aMock()
            ->onTaskSend()->respondingWith('it failed', true)->and()
            ->build()[1]
            ->toArray();

        $this->assertStringContainsString('"state": "failed"', $handler['httpResponseTemplate']['template']);
    }

    public function testStreamingProducesSseExpectationAndAdvertisesCapability(): void
    {
        $expectations = A2aMockBuilder::a2aMock('/a2a')
            ->withStreaming()
            ->withDefaultTaskResponse('streamed result')
            ->build();

        // agent-card, streaming, tasks/send, tasks/get, tasks/cancel
        $this->assertCount(5, $expectations);

        $card = $expectations[0]->toArray()['httpResponse']['body'];
        $this->assertStringContainsString('"streaming": true', $card);

        $streaming = $expectations[1]->toArray();
        $this->assertSame(
            ['type' => 'JSON_RPC', 'method' => 'message/stream'],
            $streaming['httpRequest']['body']
        );
        $sse = $streaming['httpSseResponse'];
        $this->assertSame(200, $sse['statusCode']);
        $this->assertTrue($sse['closeConnection']);
        $this->assertCount(3, $sse['events']);
        $this->assertStringContainsString('"state": "working"', $sse['events'][0]['data']);
        $this->assertStringContainsString('"kind": "artifact-update"', $sse['events'][1]['data']);
        $this->assertStringContainsString('streamed result', $sse['events'][1]['data']);
        $this->assertStringContainsString('"final": true', $sse['events'][2]['data']);
    }

    public function testCustomStreamingMethodImpliesStreaming(): void
    {
        $expectations = A2aMockBuilder::a2aMock('/a2a')
            ->withStreamingMethod('tasks/sendSubscribe')
            ->build();

        $card = $expectations[0]->toArray()['httpResponse']['body'];
        $this->assertStringContainsString('"streaming": true', $card);

        $streaming = $expectations[1]->toArray();
        $this->assertSame('tasks/sendSubscribe', $streaming['httpRequest']['body']['method']);
    }

    public function testPushNotificationsProduceConfigAndDeliveryAndOmitPlainSend(): void
    {
        $expectations = A2aMockBuilder::a2aMock('/a2a')
            ->withPushNotifications('http://localhost:1234/a2a/callback')
            ->build();

        // agent-card, config/set, delivery (replaces plain send), tasks/get, tasks/cancel
        $this->assertCount(5, $expectations);

        $card = $expectations[0]->toArray()['httpResponse']['body'];
        $this->assertStringContainsString('"pushNotifications": true', $card);

        $config = $expectations[1]->toArray();
        $this->assertSame(
            ['type' => 'JSON_RPC', 'method' => 'tasks/pushNotificationConfig/set'],
            $config['httpRequest']['body']
        );
        $this->assertStringContainsString(
            '"url": "http://localhost:1234/a2a/callback"',
            $config['httpResponseTemplate']['template']
        );

        $delivery = $expectations[2]->toArray();
        $this->assertSame(
            ['type' => 'JSON_RPC', 'method' => 'tasks/send'],
            $delivery['httpRequest']['body']
        );
        $override = $delivery['httpOverrideForwardedRequest'];
        $this->assertSame('POST', $override['requestOverride']['method']);
        $this->assertSame('/a2a/callback', $override['requestOverride']['path']);
        $this->assertSame(
            ['host' => 'localhost', 'port' => 1234, 'scheme' => 'HTTP'],
            $override['requestOverride']['socketAddress']
        );
        $this->assertFalse($override['requestOverride']['secure']);
        $this->assertSame(['localhost:1234'], $override['requestOverride']['headers']['Host']);
        // literal webhook body — no JSON-RPC id, no Velocity placeholder
        $pushBody = $override['requestOverride']['body'];
        $this->assertStringContainsString('"jsonrpc": "2.0"', $pushBody);
        $this->assertStringNotContainsString('jsonRpcRawId', $pushBody);
        // caller response template echoes the request id
        $this->assertSame('VELOCITY', $override['responseTemplate']['templateType']);
        $this->assertStringContainsString('$!{request.jsonRpcRawId}', $override['responseTemplate']['template']);

        // no plain tasks/send expectation when push notifications are configured
        foreach ($expectations as $expectation) {
            $array = $expectation->toArray();
            if (
                isset($array['httpRequest']['body']['method'])
                && $array['httpRequest']['body']['method'] === 'tasks/send'
                && isset($array['httpResponseTemplate'])
            ) {
                $this->fail('Plain tasks/send template expectation should be replaced by push delivery');
            }
        }
    }

    public function testHttpsWebhookUsesPort443AndSecure(): void
    {
        $override = A2aMockBuilder::a2aMock()
            ->withPushNotifications('https://example.com/hook')
            ->build()[2]
            ->toArray()['httpOverrideForwardedRequest'];

        $this->assertSame(
            ['host' => 'example.com', 'port' => 443, 'scheme' => 'HTTPS'],
            $override['requestOverride']['socketAddress']
        );
        $this->assertTrue($override['requestOverride']['secure']);
        $this->assertSame(['example.com:443'], $override['requestOverride']['headers']['Host']);
    }

    public function testVelocityEscapingOfMetaCharactersInTaskResponse(): void
    {
        $template = A2aMockBuilder::a2aMock()
            ->withDefaultTaskResponse('cost is $5 #1')
            ->build()[1]
            ->toArray()['httpResponseTemplate']['template'];

        $this->assertStringContainsString('cost is ${esc.d}5 ${esc.h}1', $template);
    }

    public function testVelocityEscapingOfBackslashBeforeHashInTaskResponse(): void
    {
        $template = A2aMockBuilder::a2aMock()
            ->withDefaultTaskResponse('a\\#b')
            ->build()[1]
            ->toArray()['httpResponseTemplate']['template'];

        // JSON-escaped "a\\#b": both backslashes precede '#', so all are escaped
        $this->assertStringContainsString('a${esc.b}${esc.b}${esc.h}b', $template);
    }

    public function testCustomAgentCardPathAndUrl(): void
    {
        $agentCard = A2aMockBuilder::a2aMock('/agent')
            ->withAgentName('Custom')
            ->withAgentCardPath('/custom-card.json')
            ->withAgentUrl('https://agents.example.com/agent')
            ->build()[0]
            ->toArray();

        $this->assertSame('/custom-card.json', $agentCard['httpRequest']['path']);
        $this->assertStringContainsString(
            '"url": "https://agents.example.com/agent"',
            $agentCard['httpResponse']['body']
        );
    }

    public function testInvalidWebhookUrlThrows(): void
    {
        $this->expectException(\InvalidArgumentException::class);
        A2aMockBuilder::a2aMock()
            ->withPushNotifications('not-a-url')
            ->build();
    }
}
