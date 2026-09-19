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
 * End-to-end tests of the user administration endpoints: listing, detail and role assignment.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@QuarkusTest
class UserResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/users";

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
    void listIsPaginatedInsideTheEnvelope() {
        support.createUser();
        support.createUser();
        given().auth().oauth2(admin).queryParam("page", 0).queryParam("size", 2).when().get(BASE)
                .then().statusCode(200).contentType("application/json")
                .body("success", is(true))
                .body("data.content.size()", is(2))
                .body("data.page", is(0))
                .body("data.size", is(2))
                .body("data.totalElements", greaterThanOrEqualTo(3))
                .body("data.totalPages", greaterThanOrEqualTo(2))
                .body("data.content[0]", not(hasKey("password")));
    }

    @Test
    void invalidPagingIs400Problem() {
        given().auth().oauth2(admin).queryParam("size", 0).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("status", is(400))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).queryParam("size", 101).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).queryParam("page", -1).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON);
    }

    @Test
    void detailIncludesAssignedRoles() {
        TestUser target = support.createUser("ADMIN");
        given().auth().oauth2(admin).when().get(BASE + "/" + target.id())
                .then().statusCode(200)
                .body("data.email", is(target.email()))
                .body("data.roles.size()", is(1))
                .body("data.roles[0].roleName", is("ADMIN"))
                .body("data", not(hasKey("password")));
    }

    @Test
    void unknownUserIs404Problem() {
        given().auth().oauth2(admin).when().get(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON).body("title", is("Resource Not Found"));
    }

    @Test
    void assignThenRemoveRole() {
        Long roleId = support.createRole(RbacTestSupport.unique("plain"));
        TestUser target = support.createUser();
        given().auth().oauth2(admin).when().patch(BASE + "/" + target.id() + "/roles/" + roleId)
                .then().statusCode(200)
                .body("data.roles.size()", is(1))
                .body("data.roles[0].id", is(roleId.intValue()));
        // Assigning twice is idempotent.
        given().auth().oauth2(admin).when().patch(BASE + "/" + target.id() + "/roles/" + roleId)
                .then().statusCode(200).body("data.roles.size()", is(1));
        given().auth().oauth2(admin).when().delete(BASE + "/" + target.id() + "/roles/" + roleId)
                .then().statusCode(200).body("data.roles", empty());
    }

    @Test
    void assignWithUnknownUserOrRoleIs404() {
        TestUser target = support.createUser();
        given().auth().oauth2(admin).when().patch(BASE + "/999999999/roles/" + support.roleId("ADMIN"))
                .then().statusCode(404).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().patch(BASE + "/" + target.id() + "/roles/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void thereIsNoUserCreationEndpoint() {
        // Users are created by feature/auth's register endpoint only, never by UserResource.
        given().auth().oauth2(admin).contentType("application/json").body("{\"email\":\"a@b.c\"}")
                .when().post(BASE).then().statusCode(405);
        given().auth().oauth2(admin).contentType("application/json").body("{}")
                .when().put(BASE + "/1").then().statusCode(405);
    }

    @Test
    void readerCanReadButNotAssign() {
        support.createRole("user-reader", "USER:READ");
        TestUser reader = support.createUser("user-reader");
        String token = support.token(reader);
        given().auth().oauth2(token).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(token).when().get(BASE + "/" + reader.id()).then().statusCode(200);
        given().auth().oauth2(token).when().patch(BASE + "/" + reader.id() + "/roles/" + support.roleId("ADMIN"))
                .then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));
        given().auth().oauth2(token).when().delete(BASE + "/" + reader.id() + "/roles/" + support.roleId("ADMIN"))
                .then().statusCode(403).contentType(PROBLEM_JSON);
    }

    @Test
    void userWithoutRolesIsForbiddenAndAnonymousIs401() {
        String token = support.token(support.createUser());
        given().auth().oauth2(token).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        given().when().get(BASE).then().statusCode(401).contentType(PROBLEM_JSON).body("status", is(401));
    }
}
