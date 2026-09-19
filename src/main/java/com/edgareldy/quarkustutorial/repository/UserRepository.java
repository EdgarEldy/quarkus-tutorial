package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
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
}
