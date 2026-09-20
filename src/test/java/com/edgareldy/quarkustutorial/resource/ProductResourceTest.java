package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the product endpoints: CRUD, pagination, category filter, validation, money
 * round trip, category deletion rule and permission denial.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Same approach as CategoryResourceTest: real HTTP against the Dev Services database, users from
// RbacTestSupport, and every category/product created here is tracked and removed in tearDown().
@QuarkusTest
class ProductResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/products";
    private static final String CATEGORIES = "/api/v1/categories";

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

    private Long category() {
        Long id = given().auth().oauth2(admin).contentType("application/json")
                .body("{\"categoryName\":\"" + RbacTestSupport.unique("cat") + "\"}").when().post(CATEGORIES)
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        categories.add(id);
        return id;
    }

    private static String body(Object categoryId, String name, String price) {
        return "{\"categoryId\":" + categoryId + ",\"productName\":\"" + name + "\",\"unitPrice\":" + price + "}";
    }

    private Long product(Long categoryId, String name, String price) {
        Long id = given().auth().oauth2(admin).contentType("application/json").body(body(categoryId, name, price))
                .when().post(BASE).then().statusCode(201).extract().jsonPath().getLong("data.id");
        products.add(id);
        return id;
    }

    private String tokenWith(String... permissions) {
        Long role = support.createRole(RbacTestSupport.unique("prod-role"), permissions);
        RbacTestSupport.TestUser user = support.createUser();
        support.grantRole(user.id(), role);
        return support.token(user);
    }

    @Test
    void _01_ShouldReturn201WithEnvelopeAndKeepMoney_WhenCreatingProduct() {
        Long cat = category();
        String json = given().auth().oauth2(admin).contentType("application/json")
                .body(body(cat, "Keyboard", "79.99")).when().post(BASE)
                .then().statusCode(201).contentType("application/json")
                .body("success", is(true))
                .body("data.id", notNullValue())
                .body("data.categoryId", is(cat.intValue()))
                .body("data.productName", is("Keyboard"))
                .extract().asString();
        Long id = io.restassured.path.json.JsonPath.from(json).getLong("data.id");
        products.add(id);
        assertEquals(0, new BigDecimal("79.99")
                .compareTo(new BigDecimal(io.restassured.path.json.JsonPath.from(json).getString("data.unitPrice"))));

        String read = given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).extract().jsonPath().getString("data.unitPrice");
        assertEquals(0, new BigDecimal("79.99").compareTo(new BigDecimal(read)));
    }

    @Test
    void _02_ShouldReturnAndUpdateProduct_WhenProductExists() {
        Long cat = category();
        Long other = category();
        Long id = product(cat, "Mouse", "10.50");
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true)).body("data.id", is(id.intValue()))
                .body("data.productName", is("Mouse"));
        given().auth().oauth2(admin).contentType("application/json").body(body(other, "Mouse 2", "12.25"))
                .when().put(BASE + "/" + id)
                .then().statusCode(200).body("data.productName", is("Mouse 2"))
                .body("data.categoryId", is(other.intValue()));
        String price = given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("data.productName", is("Mouse 2"))
                .body("data.categoryId", is(other.intValue()))
                .extract().jsonPath().getString("data.unitPrice");
        assertEquals(0, new BigDecimal("12.25").compareTo(new BigDecimal(price)));
    }

    @Test
    void _03_ShouldPaginateAndOrderById_WhenListingProducts() {
        Long cat = category();
        Long a = product(cat, "A", "1.00");
        Long b = product(cat, "B", "2.00");
        Long c = product(cat, "C", "3.00");
        List<Integer> ids = given().auth().oauth2(admin).queryParam("size", 100).when().get(BASE)
                .then().statusCode(200).body("success", is(true))
                .body("data.content.id", hasItems(a.intValue(), b.intValue(), c.intValue()))
                .extract().jsonPath().getList("data.content.id");
        assertEquals(ids.stream().sorted().toList(), ids);

        int total = given().auth().oauth2(admin).queryParam("size", 1).get(BASE)
                .jsonPath().getInt("data.totalElements");
        given().auth().oauth2(admin).queryParam("page", 1).queryParam("size", 1).when().get(BASE)
                .then().statusCode(200)
                .body("data.page", is(1)).body("data.size", is(1))
                .body("data.content.size()", is(1))
                .body("data.totalElements", greaterThanOrEqualTo(3))
                .body("data.totalPages", is(total));
    }

    @Test
    void _04_ShouldReturnOnlyRequestedCategory_WhenCategoryFilterGiven() {
        Long catA = category();
        Long catB = category();
        Long a1 = product(catA, "A1", "1.00");
        Long a2 = product(catA, "A2", "2.00");
        Long a3 = product(catA, "A3", "3.00");
        Long b1 = product(catB, "B1", "4.00");
        Long b2 = product(catB, "B2", "5.00");

        given().auth().oauth2(admin).queryParam("categoryId", catA).when().get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(a1.intValue(), a2.intValue(), a3.intValue()))
                .body("data.content.categoryId", everyItem(is(catA.intValue())))
                .body("data.totalElements", is(3));
        given().auth().oauth2(admin).queryParam("categoryId", catB).when().get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(b1.intValue(), b2.intValue()))
                .body("data.totalElements", is(2));
        given().auth().oauth2(admin).queryParam("categoryId", catA).queryParam("page", 1).queryParam("size", 2)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(a3.intValue()))
                .body("data.totalElements", is(3)).body("data.totalPages", is(2));
    }

    @Test
    void _05_ShouldReturn400Problem_WhenPageSizeIsInvalid() {
        given().auth().oauth2(admin).queryParam("size", 0).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("$", not(hasKey("success")));
    }

    @Test
    void _06_ShouldReturn404Problem_WhenProductIdUnknown() {
        Long cat = category();
        given().auth().oauth2(admin).when().get(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON).body("status", is(404))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).contentType("application/json").body(body(cat, "x", "1.00"))
                .when().put(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().delete(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _07_ShouldReturn400ProblemWithViolations_WhenBodyInvalid() {
        Long cat = category();
        List<String> bodies = List.of(
                body(cat, "  ", "1.00"),
                body(cat, "x".repeat(151), "1.00"),
                body(cat, "Bad", "0"),
                body(cat, "Bad", "-5.00"),
                body(cat, "Bad", "1.234"),
                "{\"productName\":\"Bad\",\"unitPrice\":1.00}");
        for (String json : bodies) {
            given().auth().oauth2(admin).contentType("application/json").body(json).when().post(BASE)
                    .then().statusCode(400).contentType(PROBLEM_JSON)
                    .body("status", is(400))
                    .body("violations", not(empty()))
                    .body("$", not(hasKey("success")));
        }
        Long id = product(cat, "Ok", "1.00");
        given().auth().oauth2(admin).contentType("application/json").body(body(cat, "", "1.00"))
                .when().put(BASE + "/" + id)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("violations", not(empty()));
    }

    @Test
    void _08_ShouldReturn422Problem_WhenCategoryUnknownOnCreateOrUpdate() {
        Long cat = category();
        given().auth().oauth2(admin).contentType("application/json").body(body(999999999, "Ghost", "1.00"))
                .when().post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        Long id = product(cat, "Real", "1.00");
        given().auth().oauth2(admin).contentType("application/json").body(body(999999999, "Ghost", "1.00"))
                .when().put(BASE + "/" + id)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422));
        given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).body("data.categoryId", is(cat.intValue()))
                .body("data.productName", is("Real"));
    }

    @Test
    void _09_ShouldRemoveProduct_WhenDeleteSucceeds() {
        Long id = product(category(), "Gone", "1.00");
        given().auth().oauth2(admin).when().delete(BASE + "/" + id)
                .then().statusCode(200).body("success", is(true));
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(404).contentType(PROBLEM_JSON);
    }

    @Test
    void _10_ShouldAllowCategoryDeletion_WhenItsProductIsDeleted() {
        Long cat = category();
        Long id = product(cat, "Blocker", "1.00");
        given().auth().oauth2(admin).when().delete(CATEGORIES + "/" + cat)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).when().delete(BASE + "/" + id).then().statusCode(200);
        given().auth().oauth2(admin).when().delete(CATEGORIES + "/" + cat).then().statusCode(200);
    }

    @Test
    void _11_ShouldAllowReadButNotWrite_WhenCallerIsReadOnly() {
        Long cat = category();
        Long id = product(cat, "Shared", "1.00");
        String reader = tokenWith("PRODUCT:READ");
        given().auth().oauth2(reader).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(reader).when().get(BASE + "/" + id).then().statusCode(200);
        given().auth().oauth2(reader).contentType("application/json").body(body(cat, "n", "1.00"))
                .when().post(BASE).then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));
        given().auth().oauth2(reader).contentType("application/json").body(body(cat, "n", "1.00"))
                .when().put(BASE + "/" + id).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(reader).when().delete(BASE + "/" + id)
                .then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(admin).when().get(BASE + "/" + id).then().statusCode(200)
                .body("data.productName", is("Shared"));
    }

    @Test
    void _12_ShouldReturn403Or401_WhenUserLacksPermissionOrTokenMissing() {
        String nobody = support.token(support.createUser());
        given().auth().oauth2(nobody).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(nobody).when().get(BASE + "/1").then().statusCode(403).contentType(PROBLEM_JSON);
        given().when().get(BASE).then().statusCode(401);
        given().contentType("application/json").body(body(1, "n", "1.00")).when().post(BASE)
                .then().statusCode(401);
    }
}
