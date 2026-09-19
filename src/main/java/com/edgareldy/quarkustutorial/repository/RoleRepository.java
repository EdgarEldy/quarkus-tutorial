package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.Role;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Panache repository for roles.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class RoleRepository implements PanacheRepository<Role> {

    /**
     * @param roleName the name, matched case-insensitively
     * @return the role, if any
     */
    public Optional<Role> findByRoleName(String roleName) {
        return find("lower(roleName) = ?1", roleName.toLowerCase()).firstResultOptional();
    }

    /**
     * @return all roles with their permissions loaded by one fetch join (no N+1), ordered by id
     */
    public List<Role> findAllWithPermissions() {
        return find("select distinct r from Role r left join fetch r.permissions order by r.id").list();
    }

    /**
     * @param permissionId the permission id
     * @return how many roles currently have this permission assigned
     */
    public long countByPermissionId(Long permissionId) {
        return count("from Role r join r.permissions p where p.id = ?1", permissionId);
    }
}
