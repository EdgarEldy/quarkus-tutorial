package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.AuditRow;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.Link;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.TestUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests the anti-last-admin rule in both directions: role removal from a user and permission removal from a role.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// The rule counts ROLE:WRITE holders across the whole database, and the database is shared by every
// @QuarkusTest class. Each test therefore detaches every other holder first (isolateRoleWriteHolders) and
// puts them back afterwards, so the test's own admin is deterministically the only holder.
@QuarkusTest
class LastAdminProtectionTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Inject
    RbacTestSupport support;

    private TestUser admin;
    private String token;
    private Long roleId;
    private Long roleWritePermissionId;
    private List<Link> detached;

    @BeforeEach
    void setUp() {
        roleWritePermissionId = support.permissionId("ROLE", "WRITE");
        // A dedicated role granting what the admin needs to call the endpoints, plus ROLE:WRITE itself.
        roleId = support.createRole(RbacTestSupport.unique("last-admin"), "ROLE:WRITE", "ROLE:READ", "USER:WRITE", "USER:READ");
        admin = support.createUser();
        support.grantRole(admin.id(), roleId);
        token = support.token(admin);
        detached = support.isolateRoleWriteHolders(admin.id());
    }

    @AfterEach
    void tearDown() {
        support.cleanup();
        support.restoreLinks(detached);
    }

    @Test
    void _01_ShouldRejectAndAudit_WhenRemovingRoleFromLastHolder() {
        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(422).contentType(PROBLEM_JSON)
                .body("status", is(422)).body("detail", containsString("ROLE:WRITE"))
                .body("$", not(hasKey("success")));

        // The business transaction rolled back: the admin still holds the role.
        given().auth().oauth2(token).when().get("/api/v1/users/" + admin.id())
                .then().statusCode(200).body("data.roles.id", hasItem(roleId.intValue()));
        // The rejection is audited on its own transaction, so it survives that rollback.
        List<AuditRow> rows = support.audit("ROLE_UNASSIGN_REJECTED", "USER", admin.id());
        assertEquals(1, rows.size());
        assertEquals(admin.id(), rows.get(0).actorUserId());
    }

    @Test
    void _02_ShouldRejectAndAudit_WhenRemovingRoleWriteFromOnlySource() {
        given().auth().oauth2(token).when().delete("/api/v1/roles/" + roleId + "/permissions/" + roleWritePermissionId)
                .then().statusCode(422).contentType(PROBLEM_JSON)
                .body("status", is(422)).body("detail", containsString("ROLE:WRITE"))
                .body("$", not(hasKey("success")));

        given().auth().oauth2(token).when().get("/api/v1/roles")
                .then().body("data.find { it.id == " + roleId + " }.permissions.action", hasItem("WRITE"));
        List<AuditRow> rows = support.audit("PERMISSION_REMOVE_FROM_ROLE_REJECTED", "ROLE", roleId);
        assertEquals(1, rows.size());
        assertEquals(admin.id(), rows.get(0).actorUserId());
    }

    @Test
    void _03_ShouldAllowRoleRemoval_WhenAnotherUserStillHoldsRoleWrite() {
        TestUser second = support.createUser();
        support.grantRole(second.id(), roleId);
        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(200).body("data.roles", empty());
    }

    @Test
    void _04_ShouldAllowRoleRemoval_WhenUserKeepsRoleWriteThroughAnotherRole() {
        Long backup = support.createRole(RbacTestSupport.unique("backup"), "ROLE:WRITE");
        support.grantRole(admin.id(), backup);
        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(200).body("data.roles.id", hasItem(backup.intValue()));
    }

    @Test
    void _05_ShouldAllowPermissionRemoval_WhenAnotherRoleStillGrantsItToAHolder() {
        Long backup = support.createRole(RbacTestSupport.unique("backup"), "ROLE:WRITE");
        TestUser second = support.createUser();
        support.grantRole(second.id(), backup);
        given().auth().oauth2(token).when().delete("/api/v1/roles/" + roleId + "/permissions/" + roleWritePermissionId)
                .then().statusCode(200);
    }

    @ParameterizedTest
    @CsvSource({"false,false", "true,true"})
    void _06_ShouldNotCountAsAnotherAdmin_WhenOtherHolderIsDisabledOrLocked(boolean enabled, boolean locked) {
        // The other holder is disabled (or locked): they cannot act, so the caller is the last active admin.
        TestUser other = support.createUser();
        support.grantRole(other.id(), roleId);
        support.setStatus(other.id(), enabled, locked);

        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422));
        given().auth().oauth2(token).when().delete("/api/v1/roles/" + roleId + "/permissions/" + roleWritePermissionId)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422));
    }

    @Test
    void _07_ShouldAllowRemoval_WhenOtherHolderIsEnabledAndUnlocked() {
        TestUser other = support.createUser();
        support.grantRole(other.id(), roleId);
        support.setStatus(other.id(), true, false);
        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(200);
    }

    @Test
    void _08_ShouldNeverLeaveZeroActiveAdmins_WhenRemovalsRunConcurrently() throws Exception {
        // Admin A holds roleId, admin B holds roleB; each removes the other's role at the same instant.
        // The advisory lock in removeRoleFromUser serialises them, so the second one sees the first commit.
        Long roleB = support.createRole(RbacTestSupport.unique("last-admin-b"), "ROLE:WRITE", "ROLE:READ",
                "USER:WRITE", "USER:READ");
        TestUser adminB = support.createUser();
        support.grantRole(adminB.id(), roleB);
        String tokenB = support.token(adminB);
        detached.addAll(support.isolateRoleWriteHolders(admin.id()));
        // isolate keeps only the given user, so re-detach nobody else: B is meant to stay, restore is a no-op.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 5; i++) {
                support.grantRole(admin.id(), roleId);
                support.grantRole(adminB.id(), roleB);
                CountDownLatch ready = new CountDownLatch(2);
                CountDownLatch go = new CountDownLatch(1);
                List<Future<Integer>> results = new ArrayList<>();
                results.add(pool.submit(removal(ready, go, token, adminB.id(), roleB)));
                results.add(pool.submit(removal(ready, go, tokenB, admin.id(), roleId)));
                ready.await();
                go.countDown();
                int first = results.get(0).get();
                int second = results.get(1).get();
                // The loser is refused: 422 from the rule, or 403 if the winner already stripped its permissions.
                assertTrue(!(first == 200 && second == 200), "both removals succeeded: " + first + "," + second);
                assertTrue(support.countActiveRoleWriteHolders() >= 1, "no active ROLE:WRITE holder left");
            }
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> removal(CountDownLatch ready, CountDownLatch go, String bearer, Long userId,
                                      Long role) {
        return () -> {
            ready.countDown();
            go.await();
            return given().auth().oauth2(bearer).when().delete("/api/v1/users/" + userId + "/roles/" + role)
                    .statusCode();
        };
    }
}
