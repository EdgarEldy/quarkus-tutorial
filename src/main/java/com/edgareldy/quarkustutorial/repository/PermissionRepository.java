package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.Permission;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Panache repository for permissions.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class PermissionRepository implements PanacheRepository<Permission> {

    /**
     * @param resource the resource part
     * @param action   the action part
     * @return the permission, if any
     */
    public Optional<Permission> findByResourceAndAction(String resource, String action) {
        return find("resource = ?1 and action = ?2", resource, action).firstResultOptional();
    }

    /**
     * Single query resolving every permission a user holds through all their roles.
     *
     * @param userId the user id
     * @return the distinct permissions
     */
    public List<Permission> findGrantedToUser(Long userId) {
        return find("select distinct p from User u join u.roles r join r.permissions p where u.id = ?1", userId)
                .list();
    }
}
