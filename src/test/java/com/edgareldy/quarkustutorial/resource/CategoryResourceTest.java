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
 * End-to-end tests of the category endpoints: CRUD, pagination, validation, the delete-with-products
 * rejection and permission denial.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Same approach as RoleResourceTest: real HTTP against a Dev Services database, with users created through
// RbacTestSupport. Categories and products created here are tracked and removed in tearDown().
@QuarkusTest
class CategoryResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/categories";

    @Inject
    RbacTestSupport support;

    @Inject
    EntityManager em;

    private final List<Long> categories = new ArrayList<>();
    private final List<Long> products = new ArrayList<>();
    private String admin;

    @BeforeEach
    void setUp() {
        admin = support.token(support.createUser("ADMIN"));
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            products.forEach(id -> em.createNativeQuery("delete from products where id = ?1").setParameter(1, id)
                    .executeUpdate());
            categories.forEach(id -> em.createNativeQuery("delete from categories where id = ?1")
                    .setParameter(1, id).executeUpdate());
        });
        categories.clear();
        products.clear();
        support.cleanup();
    }

    private Long createViaApi(String name) {
        Long id = given().auth().oauth2(admin).contentType("application/json")
                .body("{\"categoryName\":\"" + name + "\"}").when().post(BASE)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        categories.add(id);
        return id;
    }

    private Long insertProduct(Long categoryId) {
        Long id = QuarkusTransaction.requiringNew().call(() -> ((Number) em.createNativeQuery(
                "insert into products (category_id, product_name, unit_price) values (?1, 'P', 1.00) returning id")
                .setParameter(1, categoryId).getSingleResult()).longValue());
        products.add(id);
        return id;
    }

    private String tokenWith(String... permissions) {
        Long role = support.createRole(RbacTestSupport.unique("cat-role"), permissions);
        RbacTestSupport.TestUser user = support.createUser();
        support.grantRole(user.id(), role);
        return support.token(user);
    }

    @Test
    void _01_ShouldReturn201WithEnvelope_WhenCreatingCategory() {
        String name = RbacTestSupport.unique("cat");
        Long id = given().auth().oauth2(admin).contentType("application/json")
                .body("{\"categoryName\":\"" + name + "\"}").when().post(BASE)
                .then().statusCode(201).contentType("application/json")
                .body("success", is(true))
                .body("data.categoryName", is(name))
                .body("data.id", notNullValue())
                .extract().jsonPath().getLong("data.id");
        categories.add(id);
    }

    @Test
    void _02_ShouldReturnAndUpdateCategory_WhenCategoryExists() {
        Long id = createViaApi(RbacTestSupport.unique("cat"));
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true)).body("data.id", is(id.intValue()));
        given().auth().oauth2(admin).contentType("application/json").body("{\"categoryName\":\"Renamed\"}")
                .when().put(BASE + "/" + id)
                .then().statusCode(200).body("data.categoryName", is("Renamed"));
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("data.categoryName", is("Renamed"));
    }

    @Test
    void _03_ShouldPaginateAndOrderById_WhenListingCategories() {
        Long a = createViaApi(RbacTestSupport.unique("cat"));
        Long b = createViaApi(RbacTestSupport.unique("cat"));
        Long c = createViaApi(RbacTestSupport.unique("cat"));
        given().auth().oauth2(admin).queryParam("size", 100).when().get(BASE)
                .then().statusCode(200).body("success", is(true))
                .body("data.content.id", hasItems(a.intValue(), b.intValue(), c.intValue()))
                .body("data.content.id", contains(given().auth().oauth2(admin).queryParam("size", 100).get(BASE)
                        .jsonPath().getList("data.content.id").stream().sorted().toArray()));
        given().auth().oauth2(admin).queryParam("page", 1).queryParam("size", 1).when().get(BASE)
                .then().statusCode(200)
                .body("data.page", is(1)).body("data.size", is(1))
                .body("data.content.size()", is(1))
                .body("data.totalElements", greaterThanOrEqualTo(3))
                .body("data.totalPages", equalTo(given().auth().oauth2(admin).queryParam("size", 1).get(BASE)
                        .jsonPath().getInt("data.totalElements")));
    }

    @Test
    void _04_ShouldReturn400Problem_WhenPageSizeIsInvalid() {
        given().auth().oauth2(admin).queryParam("size", 0).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("$", not(hasKey("success")));
    }

    @Test
    void _05_ShouldReturn404Problem_WhenCategoryIdUnknown() {
        given().auth().oauth2(admin).when().get(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON).body("status", is(404))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).contentType("application/json").body("{\"categoryName\":\"x\"}")
                .when().put(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().delete(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _06_ShouldReturn400ProblemWithViolations_WhenBodyInvalid() {
        String tooLong = "x".repeat(151);
        for (String body : List.of("{\"categoryName\":\"  \"}", "{\"categoryName\":\"" + tooLong + "\"}")) {
            given().auth().oauth2(admin).contentType("application/json").body(body).when().post(BASE)
                    .then().statusCode(400).contentType(PROBLEM_JSON)
                    .body("status", is(400))
                    .body("violations", not(empty()))
                    .body("$", not(hasKey("success")));
        }
        Long id = createViaApi(RbacTestSupport.unique("cat"));
        given().auth().oauth2(admin).contentType("application/json").body("{\"categoryName\":\"\"}")
                .when().put(BASE + "/" + id)
                .then().statusCode(400).contentType(PROBLEM_JSON);
    }

    @Test
    void _07_ShouldRemoveCategory_WhenDeleteSucceeds() {
        Long id = createViaApi(RbacTestSupport.unique("cat"));
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true));
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _08_ShouldReturn422UntilProductRemoved_WhenCategoryHasProducts() {
        Long id = createViaApi(RbacTestSupport.unique("cat"));
        Long productId = insertProduct(id);
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(200);

        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from products where id = ?1")
                .setParameter(1, productId).executeUpdate());
        given().auth().oauth2(admin).when().delete(BASE + "/" + id).then().statusCode(200);
    }

    @Test
    void _09_ShouldAllowReadButNotWrite_WhenCallerIsReadOnly() {
        Long id = createViaApi(RbacTestSupport.unique("cat"));
        String reader = tokenWith("CATEGORY:READ");
        given().auth().oauth2(reader).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(reader).when().get(BASE + "/" + id).then().statusCode(200);
        given().auth().oauth2(reader).contentType("application/json").body("{\"categoryName\":\"n\"}")
                .when().post(BASE).then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));
        given().auth().oauth2(reader).contentType("application/json").body("{\"categoryName\":\"n\"}")
                .when().put(BASE + "/" + id).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(reader).when().delete(BASE + "/" + id)
                .then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(200);
    }

    @Test
    void _10_ShouldReturn403Or401_WhenUserLacksPermissionOrTokenMissing() {
        String nobody = support.token(support.createUser());
        given().auth().oauth2(nobody).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(nobody).when().get(BASE + "/1").then().statusCode(403).contentType(PROBLEM_JSON);
        given().when().get(BASE).then().statusCode(401);
        given().contentType("application/json").body("{\"categoryName\":\"n\"}").when().post(BASE)
                .then().statusCode(401);
    }
}
