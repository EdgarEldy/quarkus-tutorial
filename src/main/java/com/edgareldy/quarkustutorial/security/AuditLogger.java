package com.edgareldy.quarkustutorial.security;

import com.edgareldy.quarkustutorial.entity.AuditLog;
import com.edgareldy.quarkustutorial.repository.AuditLogRepository;
import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Records who did what to which entity and when, for every RBAC mutation and rejected attempt.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class AuditLogger {

    @Inject
    AuditLogRepository auditLogRepository;

    // The validated JWT of the current request (request scoped proxy); see AuthResource.
    @Inject
    JsonWebToken jwt;

    /**
     * Stores the audit row of an operation that succeeded. It joins the caller's transaction, so the
     * row is committed with the change it describes and vanishes with it if that change is rolled back.
     *
     * @param action     what happened, for example ROLE_CREATED
     * @param entityType the kind of entity concerned, for example ROLE
     * @param entityId   the entity id, or null
     * @param details    free text with the context
     */
    // A plain @Transactional (REQUIRED) joins the transaction of the caller, or starts one if there is
    // none. A success entry must not outlive a business transaction that later fails.
    @Transactional
    public void log(String action, String entityType, Long entityId, String details) {
        store(action, entityType, entityId, details);
    }

    /**
     * Stores the audit row of a REFUSED attempt. It runs in its own transaction so the row is kept
     * even though the caller's business transaction is then rolled back by the exception it throws.
     *
     * @param action     what was refused, for example ROLE_DELETE_REJECTED
     * @param entityType the kind of entity concerned, for example ROLE
     * @param entityId   the entity id, or null
     * @param details    free text with the context
     */
    // Transactional.TxType.REQUIRES_NEW suspends the caller's transaction and commits this insert
    // independently. It holds a second connection for a moment, which is acceptable because it only
    // happens on refusals, not on every mutation.
    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void logRejected(String action, String entityType, Long entityId, String details) {
        store(action, entityType, entityId, details);
    }

    private void store(String action, String entityType, Long entityId, String details) {
        AuditLog entry = new AuditLog();
        entry.setActorUserId(currentActor());
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setDetails(details);
        entry.setCreatedAt(Instant.now());
        auditLogRepository.persist(entry);
    }

    /** JWT subject as a user id, or null outside a request (startup, scheduler) or without a token. */
    private Long currentActor() {
        if (!Arc.container().requestContext().isActive()) {
            return null;
        }
        String subject = jwt.getSubject();
        if (subject == null) {
            return null;
        }
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
