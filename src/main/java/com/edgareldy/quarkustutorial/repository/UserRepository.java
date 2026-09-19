package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

/**
 * Panache repository for users.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// PanacheRepository<T> already provides find/persist/delete/count; only the lookups the
// services need beyond it are added. The repository is a CDI bean injected where required.
@ApplicationScoped
public class UserRepository implements PanacheRepository<User> {

    /**
     * @param email the email, matched case-insensitively
     * @return the user, if any
     */
    public Optional<User> findByEmail(String email) {
        return find("lower(email) = ?1", email.toLowerCase()).firstResultOptional();
    }

    /**
     * @param roleId the role id
     * @return how many users currently hold this role
     */
    public long countByRoleId(Long roleId) {
        return count("from User u join u.roles r where r.id = ?1", roleId);
    }

    /**
     * Loads the given users together with their roles in one fetch-join query (no N+1).
     *
     * @param ids the user ids
     * @return the users ordered by id
     */
    public List<User> findWithRolesByIds(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return find("select distinct u from User u left join fetch u.roles where u.id in ?1 order by u.id", ids)
                .list();
    }

    /**
     * @param id the user id
     * @return the user with roles loaded, if any
     */
    public Optional<User> findWithRolesById(Long id) {
        // list() rather than firstResultOptional(): limiting the rows of a query that fetch joins a
        // collection makes Hibernate page in memory. The id is unique, so at most one user comes back.
        return find("select distinct u from User u left join fetch u.roles where u.id = ?1", id)
                .list().stream().findFirst();
    }

    /**
     * @param resource the permission resource
     * @param action   the permission action
     * @return whether (count above zero) users hold a role granting this permission
     */
    public long countHolders(String resource, String action) {
        return count("from User u join u.roles r join r.permissions p where p.resource = ?1 and p.action = ?2",
                resource, action);
    }

    /**
     * @param resource the permission resource
     * @param action   the permission action
     * @param userId   a user to leave out of the count
     * @return whether (count above zero) users other than this one hold a role granting this permission
     */
    public long countHoldersExcludingUser(String resource, String action, Long userId) {
        return count("from User u join u.roles r join r.permissions p "
                + "where p.resource = ?1 and p.action = ?2 and u.id <> ?3", resource, action, userId);
    }

    /**
     * @param resource the permission resource
     * @param action   the permission action
     * @param roleId   a role to ignore
     * @return whether (count above zero) users hold the permission through a role other than this one
     */
    public long countHoldersExcludingRole(String resource, String action, Long roleId) {
        return count("from User u join u.roles r join r.permissions p "
                + "where p.resource = ?1 and p.action = ?2 and r.id <> ?3", resource, action, roleId);
    }

    /**
     * @param userId the user id
     * @param resource the permission resource
     * @param action   the permission action
     * @param roleId   a role of that user to ignore
     * @return how many of the user's other roles grant the permission
     */
    public long countUserRolesGrantingExcluding(Long userId, String resource, String action, Long roleId) {
        return count("from User u join u.roles r join r.permissions p "
                + "where u.id = ?1 and p.resource = ?2 and p.action = ?3 and r.id <> ?4",
                userId, resource, action, roleId);
    }
}
