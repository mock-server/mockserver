namespace Testcontainers.MockServer.Tests;

using System.Net;
using System.Net.Http;
using System.Text;
using DotNet.Testcontainers.Builders;
using FluentAssertions;
using Xunit;
using Xunit.Abstractions;

/// <summary>
/// Integration tests for <see cref="MockServerContainer" /> that require a running Docker daemon.
/// Tests are skipped when Docker is not available, matching the repo's assumeTrue(isDockerAvailable())
/// convention.
/// </summary>
[Trait("Category", "Integration")]
public sealed class MockServerContainerTest : IAsyncLifetime
{
    // Probed once per test run: the probe pulls and starts a container, which is
    // too slow to repeat per test and to fit a short timeout on a cold CI agent.
    private static readonly Lazy<Task<(bool Available, string Reason)>> DockerProbe = new(IsDockerAvailableAsync);

    private static Exception? _startFailure;

    private readonly MockServerContainer _container = new MockServerBuilder().Build();
    private readonly ITestOutputHelper _output;
    private bool _dockerAvailable;
    private string _dockerUnavailableReason = "Docker is not available";

    public MockServerContainerTest(ITestOutputHelper output)
    {
        _output = output;
    }

    public async Task InitializeAsync()
    {
        (_dockerAvailable, _dockerUnavailableReason) = await DockerProbe.Value;
        if (_dockerAvailable)
        {
            // Bounded: an unreachable mapped port otherwise hangs the wait strategy. After one
            // failure the remaining tests fail fast instead of each waiting out the timeout.
            if (_startFailure is not null)
            {
                throw new InvalidOperationException("MockServer container failed to start in an earlier test", _startFailure);
            }
            using var cts = new CancellationTokenSource(TimeSpan.FromMinutes(3));
            try
            {
                await _container.StartAsync(cts.Token);
            }
            catch (Exception e)
            {
                _startFailure = e;
                await _container.DisposeAsync();
                throw;
            }
        }
    }

    public async Task DisposeAsync()
    {
        if (_dockerAvailable)
        {
            await _container.DisposeAsync();
        }
    }

    [SkippableFact]
    public async Task ContainerStartsAndStatusEndpointResponds()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        using var httpClient = new HttpClient();

        // PUT /mockserver/status should return 200 with ports info
        var statusRequest = new HttpRequestMessage(HttpMethod.Put, $"{_container.GetUrl()}/mockserver/status");
        var statusResponse = await httpClient.SendAsync(statusRequest);
        statusResponse.StatusCode.Should().Be(HttpStatusCode.OK);

        // Evidence marker: dotnet-testcontainers-test.sh requires this line in the
        // TRX output to fail closed, because a skipped test otherwise reads as green.
        _output.WriteLine($"DOTNET_TESTCONTAINERS_STATUS_OK url={_container.GetUrl()} status={(int)statusResponse.StatusCode}");
    }

    [SkippableFact]
    public async Task ContainerAcceptsMockExpectationAndServesResponse()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        using var httpClient = new HttpClient();

        // Create an expectation
        var expectationJson = """
            {
                "httpRequest": {
                    "method": "GET",
                    "path": "/hello"
                },
                "httpResponse": {
                    "statusCode": 200,
                    "headers": {
                        "Content-Type": ["text/plain"]
                    },
                    "body": "world"
                }
            }
            """;

        var expectationRequest = new HttpRequestMessage(HttpMethod.Put, $"{_container.GetUrl()}/mockserver/expectation")
        {
            Content = new StringContent(expectationJson, Encoding.UTF8, "application/json")
        };

        var expectationResponse = await httpClient.SendAsync(expectationRequest);
        expectationResponse.StatusCode.Should().Be(HttpStatusCode.Created);

        // Issue a matching request
        var helloResponse = await httpClient.GetAsync($"{_container.GetUrl()}/hello");
        helloResponse.StatusCode.Should().Be(HttpStatusCode.OK);

        var body = await helloResponse.Content.ReadAsStringAsync();
        body.Should().Be("world");
    }

    [SkippableFact]
    public async Task GetUrlReturnsHttpEndpoint()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        _container.GetUrl().Should().StartWith("http://");
        _container.GetUrl().Should().Contain(":");
    }

    [SkippableFact]
    public async Task GetSecureUrlReturnsHttpsEndpoint()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        _container.GetSecureUrl().Should().StartWith("https://");
    }

    [SkippableFact]
    public async Task GetConnectionStringReturnsHostPort()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        var connectionString = _container.GetConnectionString();
        connectionString.Should().Contain(":");
        // Should be parseable as host:port
        var parts = connectionString.Split(':');
        parts.Should().HaveCount(2);
        int.TryParse(parts[1], out var port).Should().BeTrue();
        port.Should().BeGreaterThan(0);
    }

    [SkippableFact]
    public async Task GetClientReturnsWiredClient()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        // GetClient() returns a MockServerClient wired to the mapped host/port; a
        // control-plane reset proves it reaches the container.
        var client = _container.GetClient();
        client.Should().NotBeNull();
        await client.ResetAsync();

        // The client is cached across calls.
        _container.GetClient().Should().BeSameAs(client);
    }

    [SkippableFact]
    public async Task ResetClearsExpectations()
    {
        Skip.IfNot(_dockerAvailable, _dockerUnavailableReason);

        using var httpClient = new HttpClient();

        // Create an expectation
        var expectationJson = """
            {
                "httpRequest": { "method": "GET", "path": "/temp" },
                "httpResponse": { "statusCode": 200, "body": "temporary" }
            }
            """;

        var createRequest = new HttpRequestMessage(HttpMethod.Put, $"{_container.GetUrl()}/mockserver/expectation")
        {
            Content = new StringContent(expectationJson, Encoding.UTF8, "application/json")
        };
        await httpClient.SendAsync(createRequest);

        // Reset
        var resetRequest = new HttpRequestMessage(HttpMethod.Put, $"{_container.GetUrl()}/mockserver/reset");
        var resetResponse = await httpClient.SendAsync(resetRequest);
        resetResponse.StatusCode.Should().Be(HttpStatusCode.OK);

        // The expectation should no longer match (404 = no matching expectation)
        var response = await httpClient.GetAsync($"{_container.GetUrl()}/temp");
        response.StatusCode.Should().NotBe(HttpStatusCode.OK);
    }

    private static async Task<(bool Available, string Reason)> IsDockerAvailableAsync()
    {
        // Attempt to create and start a minimal container to verify Docker is reachable.
        var container = new ContainerBuilder("alpine:3.19")
            .WithEntrypoint("true")
            .Build();
        try
        {
            using var cts = new CancellationTokenSource(TimeSpan.FromSeconds(120));
            await container.StartAsync(cts.Token);
            return (true, string.Empty);
        }
        catch (Exception e)
        {
            return (false, $"Docker is not available: {e.GetType().Name}: {e.Message}");
        }
        finally
        {
            await container.DisposeAsync();
        }
    }
}
