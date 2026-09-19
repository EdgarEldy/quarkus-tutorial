package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.AuditRow;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.TestUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that every RBAC mutation, and every rejected attempt, leaves an audit row naming the acting user.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@QuarkusTest
class RbacAuditLogTest {

    @Inject
    RbacTestSupport support;

    private TestUser admin;
    private String token;

    @BeforeEach
    void setUp() {
        admin = support.createUser("ADMIN");
        token = support.token(admin);
    }

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    private void assertAudited(String action, String type, Long entityId) {
        List<AuditRow> rows = support.audit(action, type, entityId);
        assertEquals(1, rows.size(), action + " should be audited exactly once");
        assertEquals(admin.id(), rows.get(0).actorUserId(), action + " should name the caller as actor");
        assertTrue(rows.get(0).details() != null && !rows.get(0).details().isBlank());
    }

    private Long post(String url, String body) {
        return given().auth().oauth2(token).contentType("application/json").body(body).when().post(url)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
    }

    @Test
    void roleCreateUpdateDeleteAreAudited() {
        Long id = post("/api/v1/roles", "{\"roleName\":\"" + RbacTestSupport.unique("audit") + "\"}");
        support.trackRole(id);
        assertAudited("ROLE_CREATED", "ROLE", id);

        given().auth().oauth2(token).contentType("application/json")
                .body("{\"roleName\":\"" + RbacTestSupport.unique("audit2") + "\"}")
                .when().put("/api/v1/roles/" + id).then().statusCode(200);
        assertAudited("ROLE_UPDATED", "ROLE", id);

        given().auth().oauth2(token).when().delete("/api/v1/roles/" + id).then().statusCode(200);
        assertAudited("ROLE_DELETED", "ROLE", id);
    }

    @Test
    void permissionCreateUpdateDeleteAreAudited() {
        String resource = RbacTestSupport.unique("res").toUpperCase();
        Long id = post("/api/v1/permissions", "{\"resource\":\"" + resource + "\",\"action\":\"READ\"}");
        support.trackPermission(id);
        assertAudited("PERMISSION_CREATED", "PERMISSION", id);

        given().auth().oauth2(token).contentType("application/json")
                .body("{\"resource\":\"" + resource + "\",\"action\":\"WRITE\"}")
                .when().put("/api/v1/permissions/" + id).then().statusCode(200);
        assertAudited("PERMISSION_UPDATED", "PERMISSION", id);

        given().auth().oauth2(token).when().delete("/api/v1/permissions/" + id).then().statusCode(200);
        assertAudited("PERMISSION_DELETED", "PERMISSION", id);
    }

    @Test
    void permissionAssignAndRemoveOnARoleAreAudited() {
        Long roleId = support.createRole(RbacTestSupport.unique("audit"));
        Long permissionId = support.permissionId("CATEGORY", "READ");
        given().auth().oauth2(token).when().post("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                .then().statusCode(200);
        // A repeated assign changes nothing, so it must not add a second row.
        given().auth().oauth2(token).when().post("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                .then().statusCode(200);
        assertAudited("PERMISSION_ASSIGNED_TO_ROLE", "ROLE", roleId);

        given().auth().oauth2(token).when().delete("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                .then().statusCode(200);
        assertAudited("PERMISSION_REMOVED_FROM_ROLE", "ROLE", roleId);
    }

    @Test
    void roleAssignAndRemoveOnAUserAreAudited() {
        Long roleId = support.createRole(RbacTestSupport.unique("audit"));
        TestUser target = support.createUser();
        given().auth().oauth2(token).when().patch("/api/v1/users/" + target.id() + "/roles/" + roleId)
                .then().statusCode(200);
        assertAudited("ROLE_ASSIGNED_TO_USER", "USER", target.id());

        given().auth().oauth2(token).when().delete("/api/v1/users/" + target.id() + "/roles/" + roleId)
                .then().statusCode(200);
        assertAudited("ROLE_REMOVED_FROM_USER", "USER", target.id());
    }

    @Test
    void rejectedDeletesAreAuditedDespiteTheRollback() {
        Long roleId = support.createRole(RbacTestSupport.unique("audit"));
        TestUser holder = support.createUser();
        support.grantRole(holder.id(), roleId);
        given().auth().oauth2(token).when().delete("/api/v1/roles/" + roleId).then().statusCode(422);
        assertAudited("ROLE_DELETE_REJECTED", "ROLE", roleId);
        assertEquals(0, support.audit("ROLE_DELETED", "ROLE", roleId).size());

        Long permissionId = support.createPermission(RbacTestSupport.unique("res").toUpperCase(), "READ");
        given().auth().oauth2(token).when().post("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                .then().statusCode(200);
        given().auth().oauth2(token).when().delete("/api/v1/permissions/" + permissionId).then().statusCode(422);
        assertAudited("PERMISSION_DELETE_REJECTED", "PERMISSION", permissionId);
    }
}
