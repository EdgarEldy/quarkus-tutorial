package com.edgareldy.quarkustutorial.security;

import com.edgareldy.quarkustutorial.exception.AuthenticationFailedException;
import com.edgareldy.quarkustutorial.repository.BlacklistedTokenRepository;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Rejects requests whose (otherwise valid) JWT has been revoked by logout.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// A JAX-RS ContainerRequestFilter runs after Quarkus has already validated the JWT signature and
// expiry, so it only has to add the revocation check that a stateless JWT cannot do on its own.
// Throwing an HttpProblem here is rendered by quarkus-http-problem as application/problem+json.
@Provider
public class BlacklistedTokenFilter implements ContainerRequestFilter {

    @Inject
    SecurityIdentity identity;

    @Inject
    BlacklistedTokenRepository blacklistedTokenRepository;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (!identity.isAnonymous() && identity.getPrincipal() instanceof JsonWebToken jwt
                && jwt.getTokenID() != null
                && blacklistedTokenRepository.existsByJti(jwt.getTokenID())) {
            throw new AuthenticationFailedException("The token has been revoked");
        }
    }
}
