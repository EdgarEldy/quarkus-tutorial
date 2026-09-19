package com.edgareldy.quarkustutorial.health;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import javax.sql.DataSource;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

/**
 * Readiness check verifying that a real database connection can be obtained and is valid.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// SmallRye Health (MicroProfile Health) exposes checks over HTTP. Readiness ("/q/health/ready")
// answers "can this instance serve traffic right now?": a failing dependency such as the database
// makes it DOWN, so an orchestrator stops routing requests to the pod without restarting it.
// Liveness ("/q/health/live") answers "is the process itself alive?" and is handled elsewhere.
@Readiness
@ApplicationScoped
public class DatabaseHealthCheck implements HealthCheck {

    private static final String CHECK_NAME = "database";
    private static final int VALIDATION_TIMEOUT_SECONDS = 2;

    @Inject
    DataSource dataSource;

    @Override
    public HealthCheckResponse call() {
        // try-with-resources always returns the connection to the pool, even on failure.
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(VALIDATION_TIMEOUT_SECONDS)) {
                return HealthCheckResponse.up(CHECK_NAME);
            }
            return HealthCheckResponse.named(CHECK_NAME)
                    .down()
                    .withData("reason", "Connection is not valid")
                    .build();
        } catch (Exception e) {
            // Deliberately a short message only: exception details could leak host or schema info.
            return HealthCheckResponse.named(CHECK_NAME)
                    .down()
                    .withData("reason", "Unable to obtain a database connection")
                    .build();
        }
    }
}
