package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.edgareldy.quarkustutorial.rbac.RbacTestSupport;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.path.json.JsonPath;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * HTTP-level tests of the order Server-Sent Events endpoint: content type, authentication,
 * permission and delivery of a newly created order as a {@code data:} event.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// RestAssured cannot consume an endless response, so the stream is opened with the JDK HttpClient in
// asynchronous mode (sendAsync + BodyHandlers.ofLines): the call returns once the headers arrive and
// the event lines are read on a separate thread. @TestHTTPResource injects the running test server URL
// (port from %test.quarkus.http.test-port).
@QuarkusTest
class OrderStreamResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String STREAM = "/api/v1/orders/stream";
    private static final String JSON = "application/json";

    @TestHTTPResource("/")
    URL root;

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

    private String tokenWith(String... permissions) {
        Long role = support.createRole(RbacTestSupport.unique("stream-role"), permissions);
        RbacTestSupport.TestUser user = support.createUser();
        support.grantRole(user.id(), role);
        return support.token(user);
    }

    private HttpRequest.Builder streamRequest(String token) {
        return HttpRequest.newBuilder(URI.create(root.toString().replaceAll("/$", "") + STREAM))
                .header("Authorization", "Bearer " + token).header("Accept", "text/event-stream").GET();
    }

    @Test
    void streamWithoutTokenIs401() {
        given().accept("text/event-stream").when().get(STREAM).then().statusCode(401);
    }

    @Test
    void streamWithoutOrderReadIs403Problem() {
        String token = tokenWith("ORDER:WRITE");
        given().auth().oauth2(token).accept("text/event-stream").when().get(STREAM)
                .then().statusCode(403).contentType(PROBLEM_JSON);
    }

    @Test
    void createdOrderIsPushedAsADataEventWithEventStreamContentType() throws Exception {
        Long category = given().auth().oauth2(admin).contentType(JSON)
                .body("{\"categoryName\":\"" + RbacTestSupport.unique("cat") + "\"}").when()
                .post("/api/v1/categories").then().statusCode(201).extract().jsonPath().getLong("data.id");
        categories.add(category);
        Long product = given().auth().oauth2(admin).contentType(JSON)
                .body("{\"categoryId\":" + category + ",\"productName\":\"P\",\"unitPrice\":2.50}")
                .when().post("/api/v1/products").then().statusCode(201).extract().jsonPath().getLong("data.id");
        products.add(product);
        Long customer = given().auth().oauth2(admin).contentType(JSON)
                .body("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\"}").when().post("/api/v1/customers")
                .then().statusCode(201).extract().jsonPath().getLong("data.id");
        customers.add(customer);
        String reader = tokenWith("ORDER:READ");
        String writer = tokenWith("ORDER:WRITE");

        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<Stream<String>> response = client
                .sendAsync(streamRequest(reader).build(), HttpResponse.BodyHandlers.ofLines())
                .get(10, TimeUnit.SECONDS);
        try {
            assertEquals(200, response.statusCode());
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            org.hamcrest.MatcherAssert.assertThat(contentType, startsWith("text/event-stream"));

            // Read on another thread: findFirst() blocks until a data line arrives.
            CompletableFuture<String> dataLine = CompletableFuture.supplyAsync(
                    () -> response.body().filter(line -> line.startsWith("data:")).findFirst().orElse(null));
            // The response headers are out, so the subscription is in place; a short pause covers the
            // hop onto the reactive side before the order is committed.
            Thread.sleep(500);

            Long orderId = given().auth().oauth2(writer).contentType(JSON)
                    .body("{\"customerId\":" + customer + ",\"productId\":" + product + ",\"quantity\":3}")
                    .when().post("/api/v1/orders").then().statusCode(201).extract().jsonPath()
                    .getLong("data.id");

            String line = dataLine.get(10, TimeUnit.SECONDS);
            assertNotNull(line);
            JsonPath event = JsonPath.from(line.substring("data:".length()).trim());
            assertEquals(orderId, event.getLong("id"));
            assertEquals(customer, event.getLong("customerId"));
            assertEquals(product, event.getLong("productId"));
            assertEquals(3, event.getInt("quantity"));
            assertEquals(0, new java.math.BigDecimal("7.50").compareTo(new java.math.BigDecimal(event.getString("total"))));
        } finally {
            // Closing the line stream cancels the HTTP response subscription and drops the connection,
            // so the server side subscriber is released and nothing leaks into other tests.
            response.body().close();
        }
    }
}
