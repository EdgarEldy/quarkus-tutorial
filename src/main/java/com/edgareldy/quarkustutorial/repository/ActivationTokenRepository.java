package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.ActivationToken;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

/**
 * Panache repository for activation tokens.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */

@ApplicationScoped
public class ActivationTokenRepository implements PanacheRepository<ActivationToken> {

    /**
     * @param tokenHash SHA-256 hex of the raw token
     * @return the matching token, if any
     */
    public Optional<ActivationToken> findByToken(String tokenHash) {
        return find("token", tokenHash).firstResultOptional();
    }
}
