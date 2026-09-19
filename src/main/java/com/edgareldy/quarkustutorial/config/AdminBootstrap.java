package com.edgareldy.quarkustutorial.config;

import com.edgareldy.quarkustutorial.entity.User;
import com.edgareldy.quarkustutorial.repository.RoleRepository;
import com.edgareldy.quarkustutorial.repository.UserRepository;
import com.edgareldy.quarkustutorial.security.AuditLogger;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Optionally creates the first administrator at startup so that someone can hold the seeded ADMIN role.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class AdminBootstrap {

    private static final Logger LOG = Logger.getLogger(AdminBootstrap.class);

    // Optional<T> config: the properties may be absent (they only have defaults under %dev), and
    // then the bootstrap simply does nothing.
    @ConfigProperty(name = "app.bootstrap-admin.email")
    Optional<String> email;

    @ConfigProperty(name = "app.bootstrap-admin.password")
    Optional<String> password;

    @Inject
    UserRepository userRepository;

    @Inject
    RoleRepository roleRepository;

    @Inject
    AuditLogger auditLogger;

    // @Observes StartupEvent: CDI calls this once when the application starts, after the Flyway
    // migrations (which seed the ADMIN role) have run. Idempotent: an existing user is left alone.
    @Transactional
    void onStart(@Observes StartupEvent event) {
        if (email.isEmpty() || password.isEmpty() || email.get().isBlank() || password.get().isBlank()) {
            return;
        }
        String address = email.get().trim().toLowerCase();
        if (userRepository.findByEmail(address).isPresent()) {
            return;
        }
        var admin = roleRepository.findByRoleName("ADMIN");
        if (admin.isEmpty()) {
            LOG.warn("Bootstrap admin skipped: the ADMIN role does not exist");
            return;
        }
        User user = new User();
        user.setFirstName("Admin");
        user.setLastName("Bootstrap");
        user.setEmail(address);
        user.setPassword(BcryptUtil.bcryptHash(password.get()));
        user.setEnabled(true);
        user.setAccountLocked(false);
        user.getRoles().add(admin.get());
        userRepository.persist(user);
        auditLogger.log("BOOTSTRAP_ADMIN_CREATED", "USER", user.getId(), "email " + address);
        LOG.infof("Bootstrap admin %s created", address);
    }
}
