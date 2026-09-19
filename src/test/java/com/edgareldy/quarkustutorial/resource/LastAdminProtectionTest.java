package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.AuditRow;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.Link;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.TestUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
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
    void removingTheRoleFromTheLastHolderIsRejectedAndAudited() {
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
    void removingRoleWritePermissionFromTheOnlySourceIsRejectedAndAudited() {
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
    void roleRemovalIsAllowedWhenAnotherUserStillHoldsRoleWrite() {
        TestUser second = support.createUser();
        support.grantRole(second.id(), roleId);
        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(200).body("data.roles", empty());
    }

    @Test
    void roleRemovalIsAllowedWhenTheUserKeepsRoleWriteThroughAnotherRole() {
        Long backup = support.createRole(RbacTestSupport.unique("backup"), "ROLE:WRITE");
        support.grantRole(admin.id(), backup);
        given().auth().oauth2(token).when().delete("/api/v1/users/" + admin.id() + "/roles/" + roleId)
                .then().statusCode(200).body("data.roles.id", hasItem(backup.intValue()));
    }

    @Test
    void permissionRemovalIsAllowedWhenAnotherRoleStillGrantsItToAHolder() {
        Long backup = support.createRole(RbacTestSupport.unique("backup"), "ROLE:WRITE");
        TestUser second = support.createUser();
        support.grantRole(second.id(), backup);
        given().auth().oauth2(token).when().delete("/api/v1/roles/" + roleId + "/permissions/" + roleWritePermissionId)
                .then().statusCode(200);
    }
}
