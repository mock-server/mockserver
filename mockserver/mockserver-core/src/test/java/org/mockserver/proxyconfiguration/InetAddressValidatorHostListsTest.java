package org.mockserver.proxyconfiguration;

import org.junit.Test;
import org.mockserver.configuration.Configuration;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.Assert.assertThrows;
import static org.mockserver.model.HttpRequest.request;

/**
 * forwardProxyAllowedHosts and forwardProxyDeniedHosts, applied where forwardProxyBlockPrivateNetworks is: to the name
 * a target is given by and to the address it resolves to. Each configuration sets both lists, so none falls back to
 * the global properties.
 */
public class InetAddressValidatorHostListsTest {

    private static final InetAddress PUBLIC = address("203.0.113.10");
    private static final InetAddress PRIVATE = address("10.1.2.3");

    private static InetAddress address(String literal) {
        try {
            return InetAddress.getByName(literal);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Configuration lists(String allowed, String denied) {
        return Configuration.configuration()
            .forwardProxyBlockPrivateNetworks(false)
            .forwardProxyAllowedHosts(allowed)
            .forwardProxyDeniedHosts(denied);
    }

    @Test
    public void shouldCheckNothingWhenNeitherListIsSet() {
        Configuration unset = lists("", "");
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("rebinding.example", 80);

        try (HostLookups lookups = HostLookups.answer("rebinding.example", PRIVATE)) {
            assertThat(InetAddressValidator.validateForwardTarget(unset, unresolved), sameInstance(unresolved));
            InetAddressValidator.validateForwardTarget(unset, "rebinding.example");
            assertThat(lookups.lookups(), is(0));
        }
        assertThat(InetAddressValidator.isActive(unset), is(false));
    }

    @Test
    public void shouldAllowOnlyTheAllowedHosts() {
        Configuration allowList = lists("api.example.com,*.partner.example", "");

        InetAddressValidator.validateForwardTarget(allowList, "api.example.com");
        InetAddressValidator.validateForwardTarget(allowList, "eu.partner.example");
        ForwardTargetBlockedException refused = assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(allowList, "elsewhere.example.com"));
        assertThat(refused.getMessage(), containsString("not in forwardProxyAllowedHosts"));
        assertThat(InetAddressValidator.isActive(allowList), is(true));
    }

    @Test
    public void shouldRefuseTheDeniedHosts() {
        Configuration denyList = lists("", "*.internal.example");

        InetAddressValidator.validateForwardTarget(denyList, "api.example.com");
        ForwardTargetBlockedException refused = assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(denyList, "db.internal.example"));
        assertThat(refused.getMessage(), containsString("denied by forwardProxyDeniedHosts"));
    }

    @Test
    public void shouldLetTheDenyListWinOverTheAllowList() {
        Configuration both = lists("*.example.com", "admin.example.com");

        InetAddressValidator.validateForwardTarget(both, "api.example.com");
        assertThrows(ForwardTargetBlockedException.class, () -> InetAddressValidator.validateForwardTarget(both, "admin.example.com"));
    }

    @Test
    public void shouldNotLookANameUpWhenTheListsHoldOnlyNames() {
        Configuration namesOnly = lists("*.example.com", "admin.example.com");
        InetSocketAddress unresolved = InetSocketAddress.createUnresolved("api.example.com", 443);

        try (HostLookups lookups = HostLookups.answer("api.example.com", PRIVATE)) {
            InetAddressValidator.validateForwardTarget(namesOnly, "api.example.com");
            assertThat(InetAddressValidator.validateForwardTarget(namesOnly, unresolved), sameInstance(unresolved));
            assertThat(lookups.lookups(), is(0));
        }
    }

    @Test
    public void shouldRefuseANameThatResolvesIntoADeniedCidr() {
        Configuration denyCidr = lists("", "10.0.0.0/8");

        try (HostLookups lookups = HostLookups.answer("innocent.example", PRIVATE)) {
            ForwardTargetBlockedException refused = assertThrows(ForwardTargetBlockedException.class,
                () -> InetAddressValidator.validateForwardTarget(denyCidr, "innocent.example"));
            assertThat(refused.getMessage(), containsString("innocent.example (10.1.2.3)"));
            assertThat(lookups.lookups(), is(1));
        }
    }

    @Test
    public void shouldAllowANameThatResolvesIntoAnAllowedCidr() {
        Configuration allowCidr = lists("203.0.113.0/24", "");

        try (HostLookups ignored = HostLookups.answer("public.example", PUBLIC)) {
            InetAddressValidator.validateForwardTarget(allowCidr, "public.example");
        }
        try (HostLookups ignored = HostLookups.answer("public.example", PRIVATE)) {
            assertThrows(ForwardTargetBlockedException.class, () -> InetAddressValidator.validateForwardTarget(allowCidr, "public.example"));
        }
    }

    @Test
    public void shouldConnectToTheAddressCheckedSoARebindingNameCannotReachADeniedCidr() {
        Configuration denyCidr = lists("", "10.0.0.0/8");

        try (HostLookups lookups = HostLookups.answer("rebinding.example", PUBLIC, PRIVATE)) {
            InetSocketAddress connectTo = InetAddressValidator.validateForwardTarget(denyCidr, InetSocketAddress.createUnresolved("rebinding.example", 8080));

            assertThat(connectTo.getAddress(), is(PUBLIC));
            assertThat(connectTo.getHostString(), is("rebinding.example"));
            assertThat(connectTo.getPort(), is(8080));
            // the name's later answer is refused when it is checked again
            assertThrows(ForwardTargetBlockedException.class, () -> InetAddressValidator.validateForwardTarget(denyCidr, "rebinding.example"));
            assertThat(lookups.lookups(), is(2));
        }
    }

    @Test
    public void shouldCheckAnAlreadyResolvedAddressByItsNameAndAddress() throws UnknownHostException {
        Configuration both = lists("*.example.com", "10.0.0.0/8");

        InetAddressValidator.validateForwardTarget(both, InetAddress.getByAddress("api.example.com", PUBLIC.getAddress()));
        assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(both, InetAddress.getByAddress("api.example.com", PRIVATE.getAddress())));
        assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(both, InetAddress.getByAddress("other.net", PUBLIC.getAddress())));
    }

    @Test
    public void shouldCheckTheDestinationOfAForwardedRequest() {
        Configuration denyList = lists("", "blocked.example");

        InetAddressValidator.validateForwardTarget(denyList, request("/").withHeader("Host", "allowed.example:8080"), null, false);
        assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(denyList, request("/").withHeader("Host", "blocked.example:8080"), null, false));
        assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(denyList, request("/").withHeader("Host", "blocked.example"), InetSocketAddress.createUnresolved("upstream-proxy.example", 3128), true));
    }

    @Test
    public void shouldApplyTheListsAndTheBlockOnPrivateNetworksTogether() {
        Configuration hardened = lists("*.example.com,127.0.0.1", "").forwardProxyBlockPrivateNetworks(true);

        ForwardTargetBlockedException loopback = assertThrows(ForwardTargetBlockedException.class,
            () -> InetAddressValidator.validateForwardTarget(hardened, "127.0.0.1"));
        assertThat(loopback.getMessage(), containsString("loopback"));
        assertThrows(ForwardTargetBlockedException.class, () -> InetAddressValidator.validateForwardTarget(hardened, "8.8.8.8"));
    }

    @Test
    public void shouldRejectAnInvalidListWhenItIsSet() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
            () -> Configuration.configuration().forwardProxyAllowedHosts("10.0.0.0/40"));
        assertThat(refused.getMessage(), containsString("10.0.0.0/40"));
    }
}
