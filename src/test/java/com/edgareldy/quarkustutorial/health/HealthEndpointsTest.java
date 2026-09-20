package com.edgareldy.quarkustutorial.health;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the readiness and liveness endpoints.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest boots the whole application (with a Dev Services PostgreSQL) once and lets RestAssured
// call the real HTTP endpoints on the fixed test port, so the health checks run against a live database.
@QuarkusTest
class HealthEndpointsTest {

    @Test
    void _01_ShouldReportUpWithDatabaseCheck_WhenReadinessQueried() {
        given().when().get("/q/health/ready")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks.find { it.name == 'database' }.status", is("UP"));
    }

    @Test
    void _02_ShouldReportUpWithApplicationCheck_WhenLivenessQueried() {
        given().when().get("/q/health/live")
                .then().statusCode(200)
                .body("status", is("UP"))
                .body("checks.find { it.name == 'application' }.status", is("UP"));
    }
}
