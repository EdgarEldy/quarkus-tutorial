package com.edgareldy.quarkustutorial.repository;

import com.edgareldy.quarkustutorial.entity.BlacklistedToken;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Panache repository for revoked JWTs.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */

@ApplicationScoped
public class BlacklistedTokenRepository implements PanacheRepository<BlacklistedToken> {

    /**
     * @param jti the JWT identifier
     * @return true when a token with this jti has been revoked
     */
    public boolean existsByJti(String jti) {
        return count("jti", jti) > 0;
    }
}
