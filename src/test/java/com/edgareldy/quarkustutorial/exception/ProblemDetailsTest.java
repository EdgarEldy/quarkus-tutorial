package com.edgareldy.quarkustutorial.exception;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * End-to-end proof that errors are rendered as RFC 9457 application/problem+json, never as an ApiResponse.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// quarkus-http-problem maps HttpProblem subclasses, validation failures and unexpected exceptions to
// problem+json. Going through real HTTP (with ThrowingTestResource) verifies the wire format, not just the objects.
@QuarkusTest
class ProblemDetailsTest {

    private static final String PROBLEM_JSON = "application/problem+json";

    @Test
    void _01_ShouldReturnProblem404_WhenResourceNotFound() {
        given().when().get("/test-support/not-found")
                .then().statusCode(404)
                .contentType(PROBLEM_JSON)
                .body("type", is(ResourceNotFoundException.TYPE.toString()))
                .body("title", is("Resource Not Found"))
                .body("status", is(404))
                .body("detail", is("Thing 42 does not exist"))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _02_ShouldReturnProblem422_WhenBusinessRuleViolated() {
        given().when().get("/test-support/business-rule")
                .then().statusCode(422)
                .contentType(PROBLEM_JSON)
                .body("type", is(BusinessRuleException.TYPE.toString()))
                .body("title", is("Business Rule Violation"))
                .body("status", is(422))
                .body("detail", is("Rule X forbids this"))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _03_ShouldReturnProblem400WithViolations_WhenBeanValidationFails() {
        given().contentType("application/json").body("{\"name\":\"\"}")
                .when().post("/test-support/validate")
                .then().statusCode(400)
                .contentType(PROBLEM_JSON)
                .body("status", is(400))
                .body("violations", not(empty()))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _04_ShouldReturnSuccessEnvelope_WhenRequestIsValid() {
        given().contentType("application/json").body("{\"name\":\"bob\"}")
                .when().post("/test-support/validate")
                .then().statusCode(200)
                .body("success", is(true))
                .body("data", is("bob"));
    }

    @Test
    void _05_ShouldReturnGeneric500WithoutLeaks_WhenUnexpectedExceptionThrown() {
        String body = given().when().get("/test-support/unexpected")
                .then().statusCode(500)
                .contentType(PROBLEM_JSON)
                .body("status", is(500))
                .body("$", not(hasKey("success")))
                .extract().asString();
        assertFalse(body.contains("IllegalStateException"), body);
        assertFalse(body.contains("secret internal detail"), body);
        assertFalse(body.contains("stacktrace") || body.contains("\tat ") || body.contains(".java:"), body);
    }
}
