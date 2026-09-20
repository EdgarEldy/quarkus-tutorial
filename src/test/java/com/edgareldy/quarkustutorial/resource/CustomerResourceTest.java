package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the customer endpoints: CRUD, pagination, validation, optional fields and permission denial.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Same approach as CategoryResourceTest: real HTTP against Dev Services, users built via RbacTestSupport.
// Customers created here are tracked and removed in tearDown().
@QuarkusTest
class CustomerResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/customers";
    private static final String VALID = "{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\","
            + "\"telephone\":\"123\",\"email\":\"ada@example.com\",\"address\":\"London\"}";

    @Inject
    RbacTestSupport support;

    @Inject
    EntityManager em;

    private final List<Long> customers = new ArrayList<>();
    private String admin;

    @BeforeEach
    void setUp() {
        admin = support.token(support.createUser("ADMIN"));
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> customers.forEach(id -> em
                .createNativeQuery("delete from customers where id = ?1").setParameter(1, id).executeUpdate()));
        customers.clear();
        support.cleanup();
    }

    private Long createViaApi(String body) {
        Long id = given().auth().oauth2(admin).contentType("application/json").body(body).when().post(BASE)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        customers.add(id);
        return id;
    }

    private String tokenWith(String... permissions) {
        Long role = support.createRole(RbacTestSupport.unique("cust-role"), permissions);
        RbacTestSupport.TestUser user = support.createUser();
        support.grantRole(user.id(), role);
        return support.token(user);
    }

    @Test
    void _01_ShouldReturn201WithEnvelope_WhenCreatingCustomer() {
        Long id = given().auth().oauth2(admin).contentType("application/json").body(VALID).when().post(BASE)
                .then().statusCode(201).contentType("application/json")
                .body("success", is(true))
                .body("data.id", notNullValue())
                .body("data.firstName", is("Ada"))
                .body("data.lastName", is("Lovelace"))
                .body("data.telephone", is("123"))
                .body("data.email", is("ada@example.com"))
                .body("data.address", is("London"))
                .extract().jsonPath().getLong("data.id");
        customers.add(id);
    }

    @Test
    void _02_ShouldAcceptRequest_WhenOptionalFieldsOmittedOrNull() {
        Long id = createViaApi("{\"firstName\":\"Min\",\"lastName\":\"Imal\"}");
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200)
                .body("data.firstName", is("Min"))
                .body("data.telephone", nullValue())
                .body("data.email", nullValue())
                .body("data.address", nullValue());
        Long other = createViaApi("{\"firstName\":\"N\",\"lastName\":\"U\",\"telephone\":null,\"email\":null,"
                + "\"address\":null}");
        given().auth().oauth2(admin).when().get(BASE + "/" + other)
                .then().statusCode(200).body("data.email", nullValue());
    }

    @Test
    void _03_ShouldReturnAndUpdateCustomer_WhenCustomerExists() {
        Long id = createViaApi(VALID);
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true)).body("data.id", is(id.intValue()));
        given().auth().oauth2(admin).contentType("application/json")
                .body("{\"firstName\":\"Grace\",\"lastName\":\"Hopper\"}")
                .when().put(BASE + "/" + id)
                .then().statusCode(200).body("data.firstName", is("Grace")).body("data.email", nullValue());
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("data.lastName", is("Hopper")).body("data.address", nullValue());
    }

    @Test
    void _04_ShouldPaginateAndOrderById_WhenListingCustomers() {
        Long a = createViaApi(VALID);
        Long b = createViaApi(VALID);
        Long c = createViaApi(VALID);
        List<Integer> ids = given().auth().oauth2(admin).queryParam("size", 100).when().get(BASE)
                .then().statusCode(200).body("success", is(true))
                .body("data.content.id", hasItems(a.intValue(), b.intValue(), c.intValue()))
                .extract().jsonPath().getList("data.content.id", Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(ids.stream().sorted().toList(), ids);
        given().auth().oauth2(admin).queryParam("page", 1).queryParam("size", 1).when().get(BASE)
                .then().statusCode(200)
                .body("data.page", is(1)).body("data.size", is(1))
                .body("data.content.size()", is(1))
                .body("data.totalElements", greaterThanOrEqualTo(3))
                .body("data.totalPages", equalTo(given().auth().oauth2(admin).queryParam("size", 1).get(BASE)
                        .jsonPath().getInt("data.totalElements")));
    }

    @Test
    void _05_ShouldReturn400Problem_WhenPageSizeIsInvalid() {
        given().auth().oauth2(admin).queryParam("size", 0).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("$", not(hasKey("success")));
        given().auth().oauth2(admin).queryParam("size", 101).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).queryParam("page", -1).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON);
    }

    @Test
    void _06_ShouldReturn404Problem_WhenCustomerIdUnknown() {
        given().auth().oauth2(admin).when().get(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON).body("status", is(404))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).contentType("application/json").body(VALID)
                .when().put(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().delete(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _07_ShouldReturn400ProblemWithViolations_WhenBodyInvalid() {
        List<String> bodies = List.of(
                "{\"firstName\":\"  \",\"lastName\":\"L\"}",
                "{\"firstName\":\"F\",\"lastName\":\"\"}",
                "{\"lastName\":\"L\"}",
                "{\"firstName\":\"F\",\"lastName\":\"L\",\"email\":\"not-an-email\"}",
                "{\"firstName\":\"" + "x".repeat(101) + "\",\"lastName\":\"L\"}",
                "{\"firstName\":\"F\",\"lastName\":\"" + "x".repeat(101) + "\"}",
                "{\"firstName\":\"F\",\"lastName\":\"L\",\"telephone\":\"" + "1".repeat(51) + "\"}",
                "{\"firstName\":\"F\",\"lastName\":\"L\",\"address\":\"" + "a".repeat(256) + "\"}",
                "{\"firstName\":\"F\",\"lastName\":\"L\",\"email\":\"" + "a".repeat(250) + "@example.com\"}");
        Long id = createViaApi(VALID);
        for (String body : bodies) {
            given().auth().oauth2(admin).contentType("application/json").body(body).when().post(BASE)
                    .then().statusCode(400).contentType(PROBLEM_JSON)
                    .body("status", is(400))
                    .body("violations", not(empty()))
                    .body("$", not(hasKey("success")));
            given().auth().oauth2(admin).contentType("application/json").body(body).when().put(BASE + "/" + id)
                    .then().statusCode(400).contentType(PROBLEM_JSON);
        }
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(200)
                .body("data.firstName", is("Ada"));
    }

    @Test
    void _08_ShouldRemoveCustomer_WhenDeleteSucceeds() {
        Long id = createViaApi(VALID);
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true));
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _09_ShouldAllowReadButNotWrite_WhenCallerIsReadOnly() {
        Long id = createViaApi(VALID);
        String reader = tokenWith("CUSTOMER:READ");
        given().auth().oauth2(reader).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(reader).when().get(BASE + "/" + id).then().statusCode(200);
        given().auth().oauth2(reader).contentType("application/json").body(VALID)
                .when().post(BASE).then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));
        given().auth().oauth2(reader).contentType("application/json").body(VALID)
                .when().put(BASE + "/" + id).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(reader).when().delete(BASE + "/" + id)
                .then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(200);
    }

    @Test
    void _10_ShouldReturn403_WhenWriteOnlyUserReads() {
        String writer = tokenWith("CUSTOMER:WRITE");
        given().auth().oauth2(writer).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        Long id = given().auth().oauth2(writer).contentType("application/json").body(VALID).when().post(BASE)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        customers.add(id);
    }

    @Test
    void _11_ShouldReturn403Or401_WhenUserLacksPermissionOrTokenMissing() {
        String nobody = support.token(support.createUser());
        given().auth().oauth2(nobody).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(nobody).when().get(BASE + "/1").then().statusCode(403).contentType(PROBLEM_JSON);
        given().when().get(BASE).then().statusCode(401);
        given().when().get(BASE + "/1").then().statusCode(401);
        given().contentType("application/json").body(VALID).when().post(BASE).then().statusCode(401);
        given().contentType("application/json").body(VALID).when().put(BASE + "/1").then().statusCode(401);
        given().when().delete(BASE + "/1").then().statusCode(401);
    }
}
