package com.edgareldy.quarkustutorial.scheduler;

import com.edgareldy.quarkustutorial.repository.ActivationTokenRepository;
import com.edgareldy.quarkustutorial.repository.BlacklistedTokenRepository;
import com.edgareldy.quarkustutorial.repository.PasswordResetTokenRepository;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.jboss.logging.Logger;

/**
 * Daily housekeeping job purging token rows past their expiry so the tables stay bounded.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class ExpiredTokenCleanupJob {

    private static final Logger LOG = Logger.getLogger(ExpiredTokenCleanupJob.class);

    @Inject
    BlacklistedTokenRepository blacklistedTokenRepository;

    @Inject
    ActivationTokenRepository activationTokenRepository;

    @Inject
    PasswordResetTokenRepository passwordResetTokenRepository;

    // quarkus-scheduler: @Scheduled runs this method by itself on the given Quartz-style cron
    // expression (here every day at 03:00), with no endpoint or caller. Deleting expired rows is
    // housekeeping only: revocation and expiry checks compare the timestamps and never depend on
    // the row still existing, so a late or missed run is harmless.
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    void scheduledCleanup() {
        cleanup();
    }

    /**
     * Bulk-deletes every blacklisted, activation and password reset token already expired.
     *
     * @return the total number of rows deleted
     */
    @Transactional
    public int cleanup() {
        Instant now = Instant.now();
        // Panache bulk delete: one DELETE ... WHERE statement per table, no entity loading.
        long deleted = blacklistedTokenRepository.delete("expiresAt < ?1", now)
                + activationTokenRepository.delete("expiresAt < ?1", now)
                + passwordResetTokenRepository.delete("expiryDate < ?1", now);
        LOG.infof("Expired token cleanup removed %d row(s)", deleted);
        return (int) deleted;
    }
}
