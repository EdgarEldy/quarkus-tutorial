package com.edgareldy.quarkustutorial.security;

import com.edgareldy.quarkustutorial.dto.auth.AuthResponse;
import com.edgareldy.quarkustutorial.entity.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;
import org.eclipse.microprofile.jwt.Claims;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Builds and signs the access JWT for an authenticated user.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class JwtIssuer {

    // The lifespan is the same smallrye.jwt.new-token.lifespan the builder applies to exp; it is read
    // here only to report expiresIn to the client.
    @ConfigProperty(name = "smallrye.jwt.new-token.lifespan")
    long lifespanSeconds;

    /**
     * Issues a token whose subject is the user id and whose jti is unique (so it can be blacklisted).
     *
     * @param user the authenticated user
     * @return the signed token with its scheme and lifetime
     */
    public AuthResponse issue(User user) {
        // smallrye-jwt-build: the signing key (smallrye.jwt.sign.key.location), the issuer and the
        // lifespan come from configuration, so only per-token claims are set here.
        String token = Jwt.claims()
                .subject(String.valueOf(user.getId()))
                .claim(Claims.jti.name(), UUID.randomUUID().toString())
                .sign();
        return new AuthResponse(token, "Bearer", lifespanSeconds);
    }
}
