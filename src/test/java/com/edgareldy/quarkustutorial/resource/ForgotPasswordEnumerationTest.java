package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.edgareldy.quarkustutorial.repository.PasswordResetTokenRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Proves forgot-password does not reveal whether an email is registered.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@QuarkusTest
class ForgotPasswordEnumerationTest {

    private static final String BASE = "/api/v1/auth";

    // Counting rows needs a transaction; QuarkusTransaction runs one around a lambda from test code.
    @Inject
    PasswordResetTokenRepository resetTokens;

    @Test
    void registeredAndUnregisteredEmailsGetTheSameAnswer() {
        String known = "known" + System.nanoTime() + "@example.com";
        given().contentType("application/json")
                .body("{\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"" + known + "\",\"password\":\"Str0ngPassw0rd!\"}")
                .when().post(BASE + "/register").then().statusCode(201);

        long before = count();
        Response registered = forgot(known);
        assertEquals(before + 1, count(), "a registered email gets a reset token");

        before = count();
        Response unknown = forgot("ghost" + System.nanoTime() + "@example.com");
        assertEquals(before, count(), "an unregistered email must create no reset token");

        assertEquals(200, registered.statusCode());
        assertEquals(registered.statusCode(), unknown.statusCode());
        assertEquals(withoutTimestamp(registered), withoutTimestamp(unknown));
    }

    private long count() {
        return QuarkusTransaction.requiringNew().call(() -> resetTokens.count());
    }

    private static Response forgot(String email) {
        return given().contentType("application/json").body("{\"email\":\"" + email + "\"}")
                .when().post(BASE + "/forgot-password");
    }

    private static Map<String, Object> withoutTimestamp(Response response) {
        Map<String, Object> body = new HashMap<>(response.jsonPath().getMap("$"));
        body.remove("timestamp");
        return body;
    }
}
