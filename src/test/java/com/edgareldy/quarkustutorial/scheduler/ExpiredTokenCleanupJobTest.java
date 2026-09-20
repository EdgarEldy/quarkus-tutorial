package com.edgareldy.quarkustutorial.scheduler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.edgareldy.quarkustutorial.entity.ActivationToken;
import com.edgareldy.quarkustutorial.entity.BlacklistedToken;
import com.edgareldy.quarkustutorial.entity.PasswordResetToken;
import com.edgareldy.quarkustutorial.repository.ActivationTokenRepository;
import com.edgareldy.quarkustutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.quarkustutorial.repository.PasswordResetTokenRepository;
import com.edgareldy.quarkustutorial.repository.UserRepository;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.TestUser;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that the cleanup job deletes expired rows from the three token tables and keeps the live ones.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// The job is called directly instead of waiting for its 03:00 cron trigger: @Scheduled only decides when the
// method runs, the behaviour worth testing is in cleanup() itself.
@QuarkusTest
class ExpiredTokenCleanupJobTest {

    @Inject
    ExpiredTokenCleanupJob job;

    @Inject
    RbacTestSupport support;

    @Inject
    UserRepository userRepository;

    @Inject
    BlacklistedTokenRepository blacklistedTokenRepository;

    @Inject
    ActivationTokenRepository activationTokenRepository;

    @Inject
    PasswordResetTokenRepository passwordResetTokenRepository;

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    private void insertTokens(TestUser owner, String tag, Instant expiry) {
        QuarkusTransaction.requiringNew().run(() -> {
            var user = userRepository.findById(owner.id());
            Instant now = Instant.now();
            BlacklistedToken b = new BlacklistedToken();
            b.setUser(user);
            b.setToken("t-" + tag);
            b.setJti("jti-" + tag);
            b.setBlacklistedAt(now);
            b.setCreatedAt(now);
            b.setExpiresAt(expiry);
            blacklistedTokenRepository.persist(b);
            ActivationToken a = new ActivationToken();
            a.setUser(user);
            a.setToken("a-" + tag);
            a.setCreatedAt(now);
            a.setExpiresAt(expiry);
            activationTokenRepository.persist(a);
            PasswordResetToken p = new PasswordResetToken();
            p.setUser(user);
            p.setToken("p-" + tag);
            p.setType(PasswordResetToken.TYPE_PASSWORD_RESET);
            p.setExpiryDate(expiry);
            passwordResetTokenRepository.persist(p);
        });
    }

    private long count(String tag) {
        return QuarkusTransaction.requiringNew().call(() ->
                blacklistedTokenRepository.count("jti", "jti-" + tag)
                        + activationTokenRepository.count("token", "a-" + tag)
                        + passwordResetTokenRepository.count("token", "p-" + tag));
    }

    @Test
    void _01_ShouldDeleteExpiredRowsOfAllTables_WhenJobRuns() {
        // Purge anything expired that other test classes left, so the count below is exact.
        job.cleanup();
        TestUser owner = support.createUser();
        String tag = RbacTestSupport.unique("clean");
        insertTokens(owner, tag + "-old", Instant.now().minus(Duration.ofHours(2)));
        insertTokens(owner, tag + "-live", Instant.now().plus(Duration.ofHours(2)));

        int deleted = job.cleanup();

        assertEquals(3, deleted);
        assertEquals(0, count(tag + "-old"));
        assertEquals(3, count(tag + "-live"));
        // A second run finds nothing left of ours to delete.
        assertEquals(0, job.cleanup());
    }
}
