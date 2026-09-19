package com.edgareldy.quarkustutorial.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.edgareldy.quarkustutorial.entity.ActivationToken;
import com.edgareldy.quarkustutorial.entity.BlacklistedToken;
import com.edgareldy.quarkustutorial.entity.PasswordResetToken;
import com.edgareldy.quarkustutorial.entity.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/**
 * Repository tests against a real PostgreSQL provided by Dev Services: user lookup and token repositories.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Dev Services: no datasource URL is configured for the test profile, so Quarkus starts a PostgreSQL
// container by itself and Flyway builds the real schema in it. There is deliberately no Testcontainers
// code here. QuarkusTransaction wraps each database interaction in a transaction started from test code.
@QuarkusTest
class UserRepositoryTest {

    @Inject
    UserRepository users;
    @Inject
    ActivationTokenRepository activations;
    @Inject
    PasswordResetTokenRepository resets;
    @Inject
    BlacklistedTokenRepository blacklist;

    private User newUser(String email) {
        User u = new User();
        u.setFirstName("A");
        u.setLastName("B");
        u.setEmail(email);
        u.setPassword("hash");
        return u;
    }

    @Test
    void findByEmailIsCaseInsensitive() {
        String email = "mixed" + System.nanoTime() + "@example.com";
        QuarkusTransaction.requiringNew().run(() -> users.persist(newUser(email)));
        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(users.findByEmail(email.toUpperCase()).isPresent());
            assertTrue(users.findByEmail(email).isPresent());
            assertTrue(users.findByEmail("nobody" + System.nanoTime() + "@example.com").isEmpty());
        });
    }

    @Test
    void duplicateEmailViolatesUniqueConstraint() {
        String email = "dup" + System.nanoTime() + "@example.com";
        QuarkusTransaction.requiringNew().run(() -> users.persist(newUser(email)));
        assertThrows(RuntimeException.class,
                () -> QuarkusTransaction.requiringNew().run(() -> {
                    users.persist(newUser(email));
                    users.flush();
                }));
    }

    @Test
    void tokenRepositoriesFindTheirRows() {
        long n = System.nanoTime();
        String email = "tok" + n + "@example.com";
        QuarkusTransaction.requiringNew().run(() -> {
            User u = newUser(email);
            users.persist(u);

            ActivationToken a = new ActivationToken();
            a.setUser(u);
            a.setToken("act-" + n);
            a.setCreatedAt(Instant.now());
            a.setExpiresAt(Instant.now().plusSeconds(60));
            activations.persist(a);

            PasswordResetToken r = new PasswordResetToken();
            r.setUser(u);
            r.setToken("rst-" + n);
            r.setType(PasswordResetToken.TYPE_PASSWORD_RESET);
            r.setExpiryDate(Instant.now().plusSeconds(60));
            resets.persist(r);

            BlacklistedToken b = new BlacklistedToken();
            b.setUser(u);
            b.setToken("jti-" + n);
            b.setJti("jti-" + n);
            b.setBlacklistedAt(Instant.now());
            b.setCreatedAt(Instant.now());
            b.setExpiresAt(Instant.now().plusSeconds(60));
            blacklist.persist(b);
        });
        QuarkusTransaction.requiringNew().run(() -> {
            assertTrue(activations.findByToken("act-" + n).isPresent());
            assertTrue(activations.findByToken("missing-" + n).isEmpty());
            assertTrue(resets.findByToken("rst-" + n).isPresent());
            assertTrue(resets.findByToken("missing-" + n).isEmpty());
            assertTrue(blacklist.existsByJti("jti-" + n));
            assertFalse(blacklist.existsByJti("other-" + n));
        });
    }
}
