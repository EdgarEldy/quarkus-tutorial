package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.AuditLog;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for audit log rows.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class AuditLogRepository implements PanacheRepository<AuditLog> {
}
