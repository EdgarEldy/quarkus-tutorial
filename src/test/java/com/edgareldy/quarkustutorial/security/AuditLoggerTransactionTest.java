package com.edgareldy.quarkustutorial.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

/**
 * Tests the transactional contract of AuditLogger: successes join the caller's transaction, refusals do not.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// The caller's transaction is opened by hand with QuarkusTransaction and rolled back by throwing, which is the
// same situation as a BusinessRuleException leaving a @Transactional service method.
@QuarkusTest
class AuditLoggerTransactionTest {

    @Inject
    AuditLogger auditLogger;

    @Inject
    RbacTestSupport support;

    @Test
    void _01_ShouldRollBackSuccessRow_WhenBusinessTransactionRollsBack() {
        String action = RbacTestSupport.unique("TX_SUCCESS");
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            auditLogger.log(action, "TEST", 1L, "must vanish");
            throw new IllegalStateException("rollback");
        }));
        assertEquals(0, support.audit(action, "TEST", 1L).size());
    }

    @Test
    void _02_ShouldKeepSuccessRow_WhenTransactionCommits() {
        String action = RbacTestSupport.unique("TX_COMMIT");
        QuarkusTransaction.requiringNew().run(() -> auditLogger.log(action, "TEST", 2L, "kept"));
        assertEquals(1, support.audit(action, "TEST", 2L).size());
    }

    @Test
    void _03_ShouldKeepRejectedRow_WhenCallerRollsBack() {
        String action = RbacTestSupport.unique("TX_REJECTED");
        assertThrows(IllegalStateException.class, () -> QuarkusTransaction.requiringNew().run(() -> {
            auditLogger.logRejected(action, "TEST", 3L, "must survive");
            throw new IllegalStateException("rollback");
        }));
        assertEquals(1, support.audit(action, "TEST", 3L).size());
    }
}
