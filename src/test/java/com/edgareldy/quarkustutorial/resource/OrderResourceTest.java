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
 * End-to-end tests of the order endpoints: creation with computed total, reads, paging, filters,
 * validation, business refusals, permission denial and the customer/product deletion rules.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Same approach as ProductResourceTest: real HTTP against Dev Services, users from RbacTestSupport. The
// API has no order deletion, so tearDown() removes orders (then customers, products, categories) with
// native SQL, in foreign key order.
@QuarkusTest
class OrderResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/orders";
    private static final String JSON = "application/json";

    @Inject
    RbacTestSupport support;

    @Inject
    EntityManager em;

    private final List<Long> categories = new ArrayList<>();
    private final List<Long> products = new ArrayList<>();
    private final List<Long> customers = new ArrayList<>();
    private String admin;

    @BeforeEach
    void setUp() {
        admin = support.token(support.createUser("ADMIN"));
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            customers.forEach(id -> em.createNativeQuery("delete from orders where customer_id = ?1")
                    .setParameter(1, id).executeUpdate());
            products.forEach(id -> em.createNativeQuery("delete from orders where product_id = ?1")
                    .setParameter(1, id).executeUpdate());
            customers.forEach(id -> em.createNativeQuery("delete from customers where id = ?1")
                    .setParameter(1, id).executeUpdate());
            products.forEach(id -> em.createNativeQuery("delete from products where id = ?1")
                    .setParameter(1, id).executeUpdate());
            categories.forEach(id -> em.createNativeQuery("delete from categories where id = ?1")
                    .setParameter(1, id).executeUpdate());
        });
        categories.clear();
        products.clear();
        customers.clear();
        support.cleanup();
    }

    private Long category() {
        Long id = given().auth().oauth2(admin).contentType(JSON)
                .body("{\"categoryName\":\"" + RbacTestSupport.unique("cat") + "\"}").when()
                .post("/api/v1/categories").then().statusCode(201).extract().jsonPath().getLong("data.id");
        categories.add(id);
        return id;
    }

    private Long product(Long categoryId, String price) {
        Long id = given().auth().oauth2(admin).contentType(JSON)
                .body("{\"categoryId\":" + categoryId + ",\"productName\":\"P\",\"unitPrice\":" + price + "}")
                .when().post("/api/v1/products").then().statusCode(201).extract().jsonPath().getLong("data.id");
        products.add(id);
        return id;
    }

    private Long customer() {
        Long id = given().auth().oauth2(admin).contentType(JSON)
                .body("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}").when().post("/api/v1/customers")
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        customers.add(id);
        return id;
    }

    private static String body(Object customerId, Object productId, Object quantity) {
        return "{\"customerId\":" + customerId + ",\"productId\":" + productId + ",\"quantity\":" + quantity + "}";
    }

    private Long order(Long customerId, Long productId, int quantity) {
        return given().auth().oauth2(admin).contentType(JSON).body(body(customerId, productId, quantity))
                .when().post(BASE).then().statusCode(201).extract().jsonPath().getLong("data.id");
    }

    private String tokenWith(String... permissions) {
        Long role = support.createRole(RbacTestSupport.unique("order-role"), permissions);
        RbacTestSupport.TestUser user = support.createUser();
        support.grantRole(user.id(), role);
        return support.token(user);
    }

    @Test
    void createReturns201WithComputedTotalAndDetailRoundTrips() {
        Long cust = customer();
        Long prod = product(category(), "2.50");

        String json = given().auth().oauth2(admin).contentType(JSON).body(body(cust, prod, 3))
                .when().post(BASE)
                .then().statusCode(201).contentType("application/json")
                .body("success", is(true))
                .body("data.id", notNullValue())
                .body("data.customerId", is(cust.intValue()))
                .body("data.productId", is(prod.intValue()))
                .body("data.quantity", is(3))
                .extract().asString();
        io.restassured.path.json.JsonPath created = io.restassured.path.json.JsonPath.from(json);
        BigDecimal total = new BigDecimal(created.getString("data.total"));
        assertEquals(new BigDecimal("7.50"), total.setScale(2));

        String read = given().auth().oauth2(admin).when().get(BASE + "/" + created.getLong("data.id"))
                .then().statusCode(200).body("success", is(true))
                .body("data.customerId", is(cust.intValue()))
                .body("data.quantity", is(3))
                .extract().jsonPath().getString("data.total");
        assertEquals(0, new BigDecimal("7.50").compareTo(new BigDecimal(read)));
    }

    @Test
    void totalIsFrozenWhenTheProductPriceChangesLater() {
        Long cat = category();
        Long cust = customer();
        Long prod = product(cat, "4.00");
        Long id = order(cust, prod, 2);
        given().auth().oauth2(admin).contentType(JSON)
                .body("{\"categoryId\":" + cat + ",\"productName\":\"P\",\"unitPrice\":10.00}")
                .when().put("/api/v1/products/" + prod).then().statusCode(200);

        String old = given().auth().oauth2(admin).when().get(BASE + "/" + id)
                .then().statusCode(200).extract().jsonPath().getString("data.total");
        assertEquals(0, new BigDecimal("8.00").compareTo(new BigDecimal(old)));
        // A new order uses the fresh price (not a stale cached product).
        String fresh = given().auth().oauth2(admin).contentType(JSON).body(body(cust, prod, 2))
                .when().post(BASE).then().statusCode(201).extract().jsonPath().getString("data.total");
        assertEquals(0, new BigDecimal("20.00").compareTo(new BigDecimal(fresh)));
    }

    @Test
    void unknownOrderIs404Problem() {
        given().auth().oauth2(admin).when().get(BASE + "/999999999")
                .then().statusCode(404).contentType(PROBLEM_JSON).body("status", is(404))
                .body("$", not(hasKey("success")));
    }

    @Test
    void listIsPaginatedAndFilteredByCustomerProductOrBoth() {
        Long cat = category();
        Long custA = customer();
        Long custB = customer();
        Long prodA = product(cat, "1.00");
        Long prodB = product(cat, "2.00");
        Long o1 = order(custA, prodA, 1);
        Long o2 = order(custA, prodB, 2);
        Long o3 = order(custB, prodA, 3);

        List<Integer> ids = given().auth().oauth2(admin).queryParam("size", 100).when().get(BASE)
                .then().statusCode(200).body("success", is(true))
                .body("data.content.id", hasItems(o1.intValue(), o2.intValue(), o3.intValue()))
                .extract().jsonPath().getList("data.content.id");
        assertEquals(ids.stream().sorted().toList(), ids);

        given().auth().oauth2(admin).queryParam("customerId", custA).when().get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(o1.intValue(), o2.intValue()))
                .body("data.totalElements", is(2));
        given().auth().oauth2(admin).queryParam("productId", prodA).when().get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(o1.intValue(), o3.intValue()))
                .body("data.totalElements", is(2));
        given().auth().oauth2(admin).queryParam("customerId", custA).queryParam("productId", prodB).when()
                .get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(o2.intValue()))
                .body("data.totalElements", is(1));
        given().auth().oauth2(admin).queryParam("customerId", custB).queryParam("productId", prodB).when()
                .get(BASE)
                .then().statusCode(200).body("data.content", empty()).body("data.totalElements", is(0));
        given().auth().oauth2(admin).queryParam("customerId", custA).queryParam("page", 1).queryParam("size", 1)
                .when().get(BASE)
                .then().statusCode(200)
                .body("data.content.id", contains(o2.intValue()))
                .body("data.page", is(1)).body("data.size", is(1))
                .body("data.totalElements", is(2)).body("data.totalPages", is(2));
    }

    @Test
    void invalidPageSizeIs400Problem() {
        given().auth().oauth2(admin).queryParam("size", 0).when().get(BASE)
                .then().statusCode(400).contentType(PROBLEM_JSON).body("$", not(hasKey("success")));
    }

    @Test
    void invalidBodyIs400ProblemWithViolations() {
        Long cust = customer();
        Long prod = product(category(), "1.00");
        List<String> bodies = List.of(
                body("null", prod, 1),
                body(cust, "null", 1),
                body(cust, prod, "null"),
                body(cust, prod, 0),
                body(cust, prod, -2),
                "{}");
        for (String json : bodies) {
            given().auth().oauth2(admin).contentType(JSON).body(json).when().post(BASE)
                    .then().statusCode(400).contentType(PROBLEM_JSON)
                    .body("status", is(400))
                    .body("violations", not(empty()))
                    .body("$", not(hasKey("success")));
        }
    }

    @Test
    void unknownCustomerOrProductIs422Problem() {
        Long cust = customer();
        Long prod = product(category(), "1.00");
        given().auth().oauth2(admin).contentType(JSON).body(body(999999999, prod, 1)).when().post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).contentType(JSON).body(body(cust, 999999999, 1)).when().post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422));
    }

    @Test
    void totalBeyondNumeric14Is422ProblemAndLargestFittingTotalIsAccepted() {
        Long cust = customer();
        Long prod = product(category(), "9999999999.99");
        given().auth().oauth2(admin).contentType(JSON).body(body(cust, prod, 100)).when().post(BASE)
                .then().statusCode(201);
        given().auth().oauth2(admin).contentType(JSON).body(body(cust, prod, 101)).when().post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).contentType(JSON).body(body(cust, prod, Integer.MAX_VALUE)).when()
                .post(BASE)
                .then().statusCode(422).contentType(PROBLEM_JSON);
    }

    @Test
    void readerCanReadButNotCreateAndWriterCanCreate() {
        Long cust = customer();
        Long prod = product(category(), "1.00");
        Long id = order(cust, prod, 1);
        String reader = tokenWith("ORDER:READ");
        given().auth().oauth2(reader).when().get(BASE).then().statusCode(200);
        given().auth().oauth2(reader).when().get(BASE + "/" + id).then().statusCode(200);
        given().auth().oauth2(reader).contentType(JSON).body(body(cust, prod, 1)).when().post(BASE)
                .then().statusCode(403).contentType(PROBLEM_JSON).body("status", is(403));

        String writer = tokenWith("ORDER:WRITE");
        given().auth().oauth2(writer).contentType(JSON).body(body(cust, prod, 1)).when().post(BASE)
                .then().statusCode(201);
        given().auth().oauth2(writer).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
    }

    @Test
    void userWithoutPermissionIs403AndNoTokenIs401() {
        String nobody = support.token(support.createUser());
        given().auth().oauth2(nobody).when().get(BASE).then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(nobody).when().get(BASE + "/1").then().statusCode(403).contentType(PROBLEM_JSON);
        given().auth().oauth2(nobody).contentType(JSON).body(body(1, 1, 1)).when().post(BASE)
                .then().statusCode(403).contentType(PROBLEM_JSON);
        given().when().get(BASE).then().statusCode(401);
        given().contentType(JSON).body(body(1, 1, 1)).when().post(BASE).then().statusCode(401);
    }

    @Test
    void productWithOrdersCannotBeDeletedUntilOrdersAreGone() {
        Long cust = customer();
        Long prod = product(category(), "1.00");
        order(cust, prod, 1);
        given().auth().oauth2(admin).when().delete("/api/v1/products/" + prod)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).when().get("/api/v1/products/" + prod).then().statusCode(200);

        removeOrders(cust);
        given().auth().oauth2(admin).when().delete("/api/v1/products/" + prod).then().statusCode(200);
    }

    @Test
    void customerWithOrdersCannotBeDeletedUntilOrdersAreGone() {
        Long cust = customer();
        Long prod = product(category(), "1.00");
        order(cust, prod, 1);
        given().auth().oauth2(admin).when().delete("/api/v1/customers/" + cust)
                .then().statusCode(422).contentType(PROBLEM_JSON).body("status", is(422))
                .body("$", not(hasKey("success")));
        given().auth().oauth2(admin).when().get("/api/v1/customers/" + cust).then().statusCode(200);

        removeOrders(cust);
        given().auth().oauth2(admin).when().delete("/api/v1/customers/" + cust).then().statusCode(200);
    }

    private void removeOrders(Long customerId) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from orders where customer_id = ?1")
                .setParameter(1, customerId).executeUpdate());
    }
}
