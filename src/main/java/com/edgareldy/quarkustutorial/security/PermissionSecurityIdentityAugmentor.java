package com.edgareldy.quarkustutorial.security;

import com.edgareldy.quarkustutorial.entity.Permission;
import com.edgareldy.quarkustutorial.repository.PermissionRepository;
import io.quarkus.security.StringPermission;
import io.quarkus.security.identity.AuthenticationRequestContext;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.security.identity.SecurityIdentityAugmentor;
import io.quarkus.security.runtime.QuarkusSecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Turns the authenticated user's roles into StringPermission objects on the security identity.
 * This is the only place permissions are resolved.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// A SecurityIdentityAugmentor is called by Quarkus after the JWT has been validated and lets the
// application enrich the identity. Here the JWT carries no roles or permissions (only the user id
// as subject), so they are loaded from the database once per request and attached, which is what
// @PermissionsAllowed("RESOURCE:ACTION") then checks against. It is applied to every authenticated
// request, so no other class ever has to look permissions up.
@ApplicationScoped
public class PermissionSecurityIdentityAugmentor implements SecurityIdentityAugmentor {

    @Inject
    PermissionRepository permissionRepository;

    @Override
    public Uni<SecurityIdentity> augment(SecurityIdentity identity, AuthenticationRequestContext context) {
        if (identity.isAnonymous()) {
            return Uni.createFrom().item(identity);
        }
        // augment() may run on the IO (event loop) thread, where blocking is forbidden, and the
        // database query is blocking JDBC. runBlocking moves the supplier to a worker thread.
        return context.runBlocking(() -> addPermissions(identity));
    }

    private SecurityIdentity addPermissions(SecurityIdentity identity) {
        Long userId;
        try {
            userId = Long.valueOf(identity.getPrincipal().getName());
        } catch (NumberFormatException e) {
            return identity;
        }
        // A worker thread has no ambient transaction, so the read runs in a short one of its own.
        List<Permission> granted = QuarkusTransaction.requiringNew()
                .call(() -> permissionRepository.findGrantedToUser(userId));
        QuarkusSecurityIdentity.Builder builder = QuarkusSecurityIdentity.builder(identity);
        // @PermissionsAllowed("USER:READ") is split on the colon into name USER and action READ,
        // so the permission added must have the same shape.
        granted.forEach(p -> builder.addPermission(new StringPermission(p.getResource(), p.getAction())));
        return builder.build();
    }
}
