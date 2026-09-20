package com.edgareldy.quarkustutorial.security;

import static org.junit.jupiter.api.Assertions.*;

import com.edgareldy.quarkustutorial.dto.auth.AuthResponse;
import com.edgareldy.quarkustutorial.entity.User;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.jwt.auth.principal.JWTParser;
import jakarta.inject.Inject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.Test;

/**
 * Verifies the tokens JwtIssuer signs: signature, subject, unique jti, issuer and expiry.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest so the issuer gets its real configuration (dev private key, issuer, lifespan) injected.
@QuarkusTest
class JwtIssuerTest {

    private static final String ISSUER = "https://api.example.com/quarkus-tutorial";

    @Inject
    JwtIssuer issuer;
    @Inject
    JWTParser parser;

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private static PublicKey devPublicKey() throws Exception {
        String pem = Files.readString(Path.of("dev-keys/publicKey.pem"))
                .replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    }

    @Test
    void _01_ShouldVerifyWithPublicKeyAndCarryExpectedClaims_WhenTokenIssued() throws Exception {
        long before = System.currentTimeMillis() / 1000;
        AuthResponse response = issuer.issue(user(42L));

        JsonWebToken jwt = parser.verify(response.token(), devPublicKey());
        assertEquals("42", jwt.getSubject());
        assertEquals(ISSUER, jwt.getIssuer());
        assertNotNull(jwt.getTokenID());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresIn());
        long exp = jwt.getExpirationTime();
        assertTrue(exp >= before + response.expiresIn() && exp <= before + response.expiresIn() + 5,
                "exp " + exp + " not consistent with expiresIn from " + before);
    }

    @Test
    void _02_ShouldGiveUniqueJti_WhenTokensIssuedTwice() throws Exception {
        PublicKey key = devPublicKey();
        String first = parser.verify(issuer.issue(user(1L)).token(), key).getTokenID();
        String second = parser.verify(issuer.issue(user(1L)).token(), key).getTokenID();
        assertNotEquals(first, second);
    }
}
