package com.edgareldy.quarkustutorial.health;

import jakarta.enterprise.context.ApplicationScoped;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;

/**
 * Liveness check reporting DOWN when JVM threads are deadlocked, otherwise UP with uptime data.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Liveness ("/q/health/live") tells the orchestrator whether to RESTART the process. It must never
// depend on the database or any other external system: a database outage would then restart every
// healthy pod for no benefit. Only conditions a restart can fix belong here, such as a deadlock.
// (Readiness, which does cover dependencies, is DatabaseHealthCheck.)
@Liveness
@ApplicationScoped
public class ApplicationLivenessCheck implements HealthCheck {

    @Override
    public HealthCheckResponse call() {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        // Cheap JVM-local check: returns null when no threads are deadlocked.
        boolean deadlocked = threads.findDeadlockedThreads() != null;

        return HealthCheckResponse.named("application")
                .status(!deadlocked)
                .withData("uptimeSeconds", ManagementFactory.getRuntimeMXBean().getUptime() / 1000)
                .withData("threadCount", threads.getThreadCount())
                .build();
    }
}
