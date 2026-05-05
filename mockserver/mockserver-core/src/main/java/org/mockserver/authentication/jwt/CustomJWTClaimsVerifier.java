package org.mockserver.authentication.jwt;

import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CustomJWTClaimsVerifier extends DefaultJWTClaimsVerifier<SecurityContext> {

    private final String expectedAudience;
    private final JWTClaimsSet exactMatchClaims;
    private final Set<String> requiredClaims;

    public CustomJWTClaimsVerifier(String expectedAudience, JWTClaimsSet exactMatchClaims, Set<String> requiredClaims) {
        super(expectedAudience, exactMatchClaims, requiredClaims);
        this.expectedAudience = expectedAudience;
        this.exactMatchClaims = exactMatchClaims;
        this.requiredClaims = requiredClaims;
    }

    @Override
    public void verify(JWTClaimsSet claimsSet, SecurityContext context) throws BadJWTException {
        if (expectedAudience != null) {
            List<String> audience = claimsSet.getAudience();
            if (audience == null || !audience.contains(expectedAudience)) {
                throw new BadJWTException("JWT audience rejected: " + (audience != null ? audience : "[]"));
            }
        }

        if (requiredClaims != null && !requiredClaims.isEmpty()) {
            Set<String> missingClaims = new HashSet<>();
            for (String requiredClaim : requiredClaims) {
                if (claimsSet.getClaim(requiredClaim) == null) {
                    missingClaims.add(requiredClaim);
                }
            }
            if (!missingClaims.isEmpty()) {
                List<String> sorted = new ArrayList<>(missingClaims);
                sorted.sort(String::compareTo);
                throw new BadJWTException("JWT missing required claims: " + sorted);
            }
        }

        if (exactMatchClaims != null && !exactMatchClaims.getClaims().isEmpty()) {
            for (Map.Entry<String, Object> entry : exactMatchClaims.getClaims().entrySet()) {
                String claimName = entry.getKey();
                Object expectedValue = entry.getValue();
                Object actualValue = claimsSet.getClaim(claimName);
                
                if (!expectedValue.equals(actualValue)) {
                    throw new BadJWTException("JWT " + claimName + " claim has value " + actualValue + ", must be " + expectedValue);
                }
            }
        }

        super.verify(claimsSet, context);
    }
}
