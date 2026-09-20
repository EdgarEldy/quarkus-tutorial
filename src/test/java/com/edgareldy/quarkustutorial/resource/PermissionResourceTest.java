package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the permission catalog endpoints.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@QuarkusTest
class PermissionResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/permissions";

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

    private Long createViaApi(String resource, String action) {
        Long id = given().auth().oauth2(admin).contentType("application/json")
                .body("{\"resource\":\"" + resource + "\",\"action\":\"" + action + "\"}")
                .when().post(BASE).then().statusCode(201).extract().jsonPath().getLong("data.id");
        support.trackPermission(id);
        return id;
    }

    @Test
    void _01_ShouldReturnSeededCatalog_WhenListingPermissions() {
        given().auth().oauth2(admin).when().get(BASE)
                .then().statusCode(200).contentType("application/json")
                .body("success", is(true))
                .body("data.size()", greaterThanOrEqualTo(14))
                .body("data.find { it.resource == 'ORDER' && it.action == 'WRITE' }", notNullValue());
    }

    @Test
    void _02_ShouldNormaliseToUpperCase_WhenCreatingPermission() {
        String resource = RbacTestSupport.unique("res").toUpperCase();
        given().auth().oauth2(admin).contentType("application/json")
                .body("{\"resource\":\"" + resource.toLowerCase() + "\",\"action\":\"exec\"}")
                .when().post(BASE)
                .then().statusCode(201).contentType("application/json")
                .body("data.resource", is(resource)).body("data.action", is("EXEC"));
        support.trackPermission(support.permissionId(resource, "EXEC"));
    }

    @Test
    void _03_ShouldReturn422Problem_WhenPermissionPairIsDuplicated() {
        String resource = RbacTestSupport.unique("res").toUpperCase();
        createViaApi(resource, "READ");
        given().auth().oauth2(admin).contentType("application/json")
                .body("{\"resource\":\"" + resource + "\",\"action\":\"READ\"}")
                .when().post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON)
                .body("status", is(422)).body("title", is("Business Rule Violation"))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _04_ShouldReturn400ValidationProblem_WhenFieldsAreBlank() {
        given().auth().oauth2(admin).contentType("application/json").body("{\"resource\":\"\",\"action\":\"\"}")
                .when().post(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("status", is(400));
    }

    @Test
    void _05_ShouldChangePairAndRejectDuplicateOrUnknown_WhenUpdatingPermission() {
        String resource = RbacTestSupport.unique("res").toUpperCase();
        Long id = createViaApi(resource, "READ");
        createViaApi(resource, "WRITE");
        given().auth().oauth2(admin).contentType("application/json")
                .body("{\"resource\":\"" + resource + "\",\"action\":\"DELETE\"}")
                .when().put(BASE + "/" + id)
                .then().statusCode(200).body("data.action", is("DELETE"));
        given().auth().oauth2(admin).contentType("application/json")
                .body("{\"resource\":\"" + resource + "\",\"action\":\"WRITE\"}")
                .when().put(BASE + "/" + id)
                .then().statusCode(422).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).contentType("application/json").body("{\"resource\":\"A\",\"action\":\"B\"}")
                .when().put(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON).body("title", is("Resource Not Found"));
    }

    @Test
    void _06_ShouldReturn404AfterRemoval_WhenPermissionDeleted() {
        Long id = createViaApi(RbacTestSupport.unique("res").toUpperCase(), "READ");
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true));
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _07_ShouldAllowListButNotWrite_WhenCallerIsReader() {
        support.createRole("perm-reader", "PERMISSION:READ");
        String token = support.token(support.createUser("perm-reader"));
        given().auth().oauth2(token).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(token).contentType("application/json").body("{\"resource\":\"A\",\"action\":\"B\"}")
                .when().post(BASE)
                .then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));
        given().auth().oauth2(token).when().delete(BASE + "/1")
                .then().statusCode(403).contentType(PROBLEM_JSON);
    }

    @Test
    void _08_ShouldReturn403Or401_WhenUserHasNoRolesOrIsAnonymous() {
        String token = support.token(support.createUser());
        given().auth().oauth2(token).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        given().when().get(BASE).then().statusCode(401).contentType(PROBLEM_JSON).body("status", is(401));
    }
}
