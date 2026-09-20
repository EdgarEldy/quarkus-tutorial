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
 * End-to-end tests of the role administration endpoints, including permission assignment and denial cases.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest runs the whole application against a Dev Services PostgreSQL that all test classes share, so
// each test creates its own uniquely named data and RbacTestSupport.cleanup() removes it afterwards.
@QuarkusTest
class RoleResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/roles";

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

    private Long createRoleViaApi(String name) {
        Long id = given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"" + name + "\"}")
                .when().post(BASE).then().statusCode(201).extract().jsonPath().getLong("data.id");
        support.trackRole(id);
        return id;
    }

    @Test
    void _01_ShouldIncludeSeededAdminWithPermissions_WhenListingRoles() {
        given().auth().oauth2(admin).when().get(BASE)
                .then().statusCode(200).contentType("application/json")
                .body("success", is(true))
                .body("data.find { it.roleName == 'ADMIN' }.permissions.size()", greaterThanOrEqualTo(14))
                .body("data.find { it.roleName == 'ADMIN' }.permissions.resource", hasItem("ROLE"));
    }

    @Test
    void _02_ShouldReturn201WithEnvelope_WhenCreatingRole() {
        String name = RbacTestSupport.unique("role");
        given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"" + name + "\"}")
                .when().post(BASE)
                .then().statusCode(201).contentType("application/json")
                .body("success", is(true))
                .body("data.roleName", is(name))
                .body("data.permissions", empty())
                .body("data.id", notNullValue());
        support.trackRole(support.roleId(name));
    }

    @Test
    void _03_ShouldReturn422Problem_WhenRoleNameIsDuplicated() {
        String name = RbacTestSupport.unique("role");
        createRoleViaApi(name);
        given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"" + name + "\"}")
                .when().post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON)
                .body("status", is(422))
                .body("title", is("Business Rule Violation"))
                .body("detail", containsString(name))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _04_ShouldReturn400ValidationProblem_WhenRoleNameIsBlank() {
        given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"  \"}")
                .when().post(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON)
                .body("status", is(400))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _05_ShouldRenameRoleAndRejectDuplicateOrUnknown_WhenUpdatingRole() {
        Long id = createRoleViaApi(RbacTestSupport.unique("role"));
        Long other = createRoleViaApi(RbacTestSupport.unique("role"));
        String renamed = RbacTestSupport.unique("renamed");
        given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"" + renamed + "\"}")
                .when().put(BASE + "/" + id)
                .then().statusCode(200).body("data.roleName", is(renamed));

        given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"" + renamed + "\"}")
                .when().put(BASE + "/" + other)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422));

        given().auth().oauth2(admin).contentType("application/json").body("{\"roleName\":\"x\"}")
                .when().put(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON)
                .body("title", is("Resource Not Found")).body("$", not(hasKey("success")));
    }

    @Test
    void _06_ShouldReturn404AfterRemoval_WhenRoleDeleted() {
        Long id = createRoleViaApi(RbacTestSupport.unique("role"));
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true)).body("message", is("Role deleted"));
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _07_ShouldAssignAndRemovePermission_WhenManagingRolePermissions() {
        Long id = createRoleViaApi(RbacTestSupport.unique("role"));
        Long readCategory = support.permissionId("CATEGORY", "READ");
        given().auth().oauth2(admin).when().post(BASE + "/" + id + "/permissions/" + readCategory)
                .then().statusCode(200)
                .body("data.permissions.size()", is(1))
                .body("data.permissions[0].resource", is("CATEGORY"))
                .body("data.permissions[0].action", is("READ"));
        // Assigning twice is idempotent.
        given().auth().oauth2(admin).when().post(BASE + "/" + id + "/permissions/" + readCategory)
                .then().statusCode(200).body("data.permissions.size()", is(1));
        given().auth().oauth2(admin).when().delete(BASE + "/" + id + "/permissions/" + readCategory)
                .then().statusCode(200).body("data.permissions", empty());
    }

    @Test
    void _08_ShouldReturn404_WhenAssigningUnknownPermissionOrRole() {
        Long id = createRoleViaApi(RbacTestSupport.unique("role"));
        given().auth().oauth2(admin).when().post(BASE + "/" + id + "/permissions/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().post(BASE + "/999999999/permissions/" + support.permissionId("USER", "READ"))
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _09_ShouldAllowListButNotWrite_WhenCallerIsReader() {
        support.createRole("reader-role", "ROLE:READ");
        TestUser reader = support.createUser("reader-role");
        String token = support.token(reader);
        given().auth().oauth2(token).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(token).contentType("application/json").body("{\"roleName\":\"nope\"}")
                .when().post(BASE)
                .then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(token).when().delete(BASE + "/1").then().statusCode(403).contentType(PROBLEM_JSON);
    }

    @Test
    void _10_ShouldReturn403_WhenUserHasNoRole() {
        String token = support.token(support.createUser());
        given().auth().oauth2(token).when().get(BASE)
                .then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));
    }

    @Test
    void _11_ShouldReturn401Problem_WhenTokenMissing() {
        given().when().get(BASE)
                .then().statusCode(401).contentType(PROBLEM_JSON).body("status", is(401))
                .body("$", not(hasKey("success")));
    }
}
