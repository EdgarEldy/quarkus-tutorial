package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import com.edgareldy.quarkustutorial.rbac.RbacTestSupport.TestUser;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that a role still held by a user, or a permission still assigned to a role, cannot be deleted.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@QuarkusTest
class ReferencedRejectionTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Inject
    RbacTestSupport support;

    private String admin;

    @BeforeEach
    void setUp() {
        admin = support.token(support.createUser("ADMIN"));
    }

    @AfterEach
    void tearDown() {
        support.cleanup();
    }

    @Test
    void _01_ShouldRejectRoleDeletion_WhenRoleIsAssignedToUser() {
        Long roleId = support.createRole(RbacTestSupport.unique("held"));
        TestUser holder = support.createUser();
        support.grantRole(holder.id(), roleId);

        given().auth().oauth2(admin).when().delete("/api/v1/roles/" + roleId)
                .then().statusCode(422).contentType(PROBLEM_JSON)
                .body("status", is(422)).body("title", is("Business Rule Violation"))
                .body("detail", containsString("still assigned"))
                .body("$", not(hasKey("success")));
        // The role survived the rejected delete.
        given().auth().oauth2(admin).when().get("/api/v1/roles")
                .then().body("data.id", hasItem(roleId.intValue()));

        given().auth().oauth2(admin).when().delete("/api/v1/users/" + holder.id() + "/roles/" + roleId)
                .then().statusCode(200);
        given().auth().oauth2(admin).when().delete("/api/v1/roles/" + roleId)
                .then().statusCode(200).body("success", is(true));
    }

    @Test
    void _02_ShouldRejectPermissionDeletion_WhenPermissionIsAssignedToRole() {
        Long permissionId = support.createPermission(RbacTestSupport.unique("res").toUpperCase(), "READ");
        Long roleId = support.createRole(RbacTestSupport.unique("carrier"));
        given().auth().oauth2(admin).when().post("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                .then().statusCode(200);

        given().auth().oauth2(admin).when().delete("/api/v1/permissions/" + permissionId)
                .then().statusCode(422).contentType(PROBLEM_JSON)
                .body("status", is(422)).body("detail", containsString("still assigned"))
                .body("$", not(hasKey("success")));

        given().auth().oauth2(admin).when().delete("/api/v1/roles/" + roleId + "/permissions/" + permissionId)
                .then().statusCode(200);
        given().auth().oauth2(admin).when().delete("/api/v1/permissions/" + permissionId)
                .then().statusCode(200).body("success", is(true));
    }
}
