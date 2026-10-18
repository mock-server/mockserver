using Xunit;

namespace MockServer.Client.IntegrationTests;

/// <summary>
/// Smoke tests for <see cref="MockServerFixture"/> that exercise the lifecycle
/// hooks without requiring a running MockServer. When MOCKSERVER_URL is unset
/// the fixture must degrade gracefully (no client, no-op hooks).
/// </summary>
// Shares the Integration collection so clearing the process-wide MOCKSERVER_URL cannot race those tests.
[Collection("Integration")]
public class MockServerFixtureTests
{
    private sealed class ProbeFixture : MockServerFixture
    {
        public bool HasClient => GetClient() != null;
        private MockServerClient? GetClient()
        {
            // Reflect the protected Client property for assertion purposes.
            var prop = typeof(MockServerFixture).GetProperty("Client",
                System.Reflection.BindingFlags.Instance | System.Reflection.BindingFlags.NonPublic);
            return prop?.GetValue(this) as MockServerClient;
        }
    }

    [Fact]
    public async Task InitializeAndDispose_AreNoOps_WhenServerUrlUnset()
    {
        var previous = Environment.GetEnvironmentVariable("MOCKSERVER_URL");
        Environment.SetEnvironmentVariable("MOCKSERVER_URL", null);
        try
        {
            var fixture = new ProbeFixture();
            await fixture.InitializeAsync();
            Assert.False(fixture.HasClient);
            // DisposeAsync must not throw when there is no client.
            await fixture.DisposeAsync();
        }
        finally
        {
            Environment.SetEnvironmentVariable("MOCKSERVER_URL", previous);
        }
    }
}
