package com.edgareldy.quarkustutorial.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * End-to-end tests of the auth endpoints: register, activate, login, /me, logout and password reset.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest boots the real application once (Dev Services provides PostgreSQL) and these tests call it
// over real HTTP with RestAssured, so filters, security and Problem Details rendering are all exercised.
// There is no mail sender, so raw tokens are read from the INFO log line the service emits, through a
// java.util.logging Handler: the flow stays fully end to end, with no shortcut into the database.
@QuarkusTest
class AuthResourceTest {

    private static final String PROBLEM_JSON = "application/problem+json";
    private static final String BASE = "/api/v1/auth";
    private static final String PASSWORD = "Str0ngPassw0rd!";

    private final List<String> logLines = new ArrayList<>();
    private final Logger serviceLogger = Logger.getLogger("com.edgareldy.quarkustutorial.service.impl.AuthServiceImpl");
    private Handler handler;

    // Collects the formatted messages the auth service logs, so the raw tokens can be read back.
    @BeforeEach
    void attachHandler() {
        handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                String message = record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : record.getMessage();
                synchronized (logLines) {
                    logLines.add(message);
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        serviceLogger.addHandler(handler);
    }

    @AfterEach
    void detachHandler() {
        serviceLogger.removeHandler(handler);
    }

    @Test
    void _01_ShouldCompleteFullFlow_WhenRegisteringActivatingLoggingInAndOut() {
        String email = uniqueEmail();
        register(email, PASSWORD).statusCode(201)
                .contentType("application/json")
                .body("success", is(true))
                .body("timestamp", notNullValue())
                .body("data.email", is(email))
                .body("data.enabled", is(false))
                .body("data", not(hasKey("password")));

        String activation = capture("Activation token for " + email + " \\(valid 24h\\): (\\S+)");
        given().queryParam("token", activation).when().get(BASE + "/activate-account")
                .then().statusCode(200)
                .body("success", is(true))
                .body("timestamp", notNullValue());

        String jwt = login(email, PASSWORD).statusCode(200)
                .body("success", is(true))
                .body("timestamp", notNullValue())
                .body("data.tokenType", is("Bearer"))
                .body("data.expiresIn", is(3600))
                .extract().path("data.token");
        assertNotNull(jwt);

        String me = given().auth().oauth2(jwt).when().get(BASE + "/me")
                .then().statusCode(200)
                .body("success", is(true))
                .body("timestamp", notNullValue())
                .body("data.email", is(email))
                .body("data.firstName", is("Ada"))
                .body("data.enabled", is(true))
                .body("data", not(hasKey("password")))
                .extract().asString();
        org.junit.jupiter.api.Assertions.assertFalse(me.toLowerCase().contains("password"), me);

        // Deliberately no Content-Type: logout has no body, so it must not demand one (regression: a
        // class-level @Consumes used to make it answer 415).
        given().auth().oauth2(jwt).when().post(BASE + "/logout")
                .then().statusCode(200).body("success", is(true));

        // The very same token, still unexpired, is now revoked.
        given().auth().oauth2(jwt).when().get(BASE + "/me")
                .then().statusCode(401)
                .contentType(PROBLEM_JSON)
                .body("status", is(401))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _02_ShouldAllowSingleUse_WhenResettingPassword() {
        String email = registerAndActivate();

        given().contentType("application/json").body("{\"email\":\"" + email + "\"}")
                .when().post(BASE + "/forgot-password")
                .then().statusCode(200).body("success", is(true));
        String reset = capture("Password reset token for " + email + " .*: (\\S+)");

        resetPassword(reset, "N3wPassw0rd!!").statusCode(200).body("success", is(true));

        login(email, PASSWORD).statusCode(401).contentType(PROBLEM_JSON);
        login(email, "N3wPassw0rd!!").statusCode(200).body("data.token", notNullValue());

        resetPassword(reset, "Another0ne!!!").statusCode(422)
                .contentType(PROBLEM_JSON)
                .body("status", is(422))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _03_ShouldReturn422_WhenRegisteringDuplicateEmail() {
        String email = uniqueEmail();
        register(email, PASSWORD).statusCode(201);
        register(email, PASSWORD).statusCode(422)
                .contentType(PROBLEM_JSON)
                .body("status", is(422))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _04_ShouldReturn400WithViolations_WhenRegisterBodyInvalid() {
        given().contentType("application/json")
                .body("{\"firstName\":\"\",\"lastName\":\"L\",\"email\":\"not-an-email\",\"password\":\"short\"}")
                .when().post(BASE + "/register")
                .then().statusCode(400)
                .contentType(PROBLEM_JSON)
                .body("status", is(400))
                .body("violations", not(empty()))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _05_ShouldBeIndistinguishable_WhenEmailUnknownOrPasswordWrong() {
        String email = registerAndActivate();
        ValidatableResponse unknown = login(uniqueEmail(), PASSWORD).statusCode(401).contentType(PROBLEM_JSON);
        ValidatableResponse wrong = login(email, "WrongPassw0rd!!").statusCode(401).contentType(PROBLEM_JSON);
        org.junit.jupiter.api.Assertions.assertEquals(
                unknown.extract().asString(), wrong.extract().asString());
        unknown.body("detail", is("Invalid email or password"));
    }

    @Test
    void _06_ShouldReturn401_WhenLoggingInBeforeActivation() {
        String email = uniqueEmail();
        register(email, PASSWORD).statusCode(201);
        login(email, PASSWORD).statusCode(401)
                .contentType(PROBLEM_JSON)
                .body("status", is(401))
                .body("$", not(hasKey("success")));
    }

    @Test
    void _07_ShouldReturn401_WhenCallingMeWithoutToken() {
        given().when().get(BASE + "/me").then().statusCode(401);
    }

    @Test
    void _08_ShouldReturn422_WhenActivatingWithBadToken() {
        given().queryParam("token", "definitely-not-a-real-token").when().get(BASE + "/activate-account")
                .then().statusCode(422)
                .contentType(PROBLEM_JSON)
                .body("status", is(422))
                .body("$", not(hasKey("success")));
    }

    private static String uniqueEmail() {
        return "user" + System.nanoTime() + "@example.com";
    }

    private ValidatableResponse register(String email, String password) {
        return given().contentType("application/json")
                .body("{\"firstName\":\"Ada\",\"lastName\":\"Lovelace\",\"email\":\"" + email
                        + "\",\"password\":\"" + password + "\"}")
                .when().post(BASE + "/register").then();
    }

    private static ValidatableResponse login(String email, String password) {
        return given().contentType("application/json")
                .body("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}")
                .when().post(BASE + "/login").then();
    }

    private static ValidatableResponse resetPassword(String token, String newPassword) {
        return given().contentType("application/json")
                .body("{\"token\":\"" + token + "\",\"newPassword\":\"" + newPassword + "\"}")
                .when().post(BASE + "/reset-password").then();
    }

    private String registerAndActivate() {
        String email = uniqueEmail();
        register(email, PASSWORD).statusCode(201);
        String token = capture("Activation token for " + email + " \\(valid 24h\\): (\\S+)");
        given().queryParam("token", token).when().get(BASE + "/activate-account").then().statusCode(200);
        return email;
    }

    private String capture(String regex) {
        Pattern pattern = Pattern.compile(regex);
        synchronized (logLines) {
            for (String line : logLines) {
                Matcher m = pattern.matcher(line);
                if (m.find()) {
                    return m.group(1);
                }
            }
        }
        throw new AssertionError("No log line matched " + regex + " in " + logLines);
    }
}
