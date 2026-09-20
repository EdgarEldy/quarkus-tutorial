package com.edgareldy.quarkustutorial.repository;

import static org.junit.jupiter.api.Assertions.*;

import com.edgareldy.quarkustutorial.entity.Customer;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Repository tests of CustomerRepository against the Dev Services PostgreSQL.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// @QuarkusTest boots the real datasource (Dev Services PostgreSQL, Flyway schema) so the mapping and the
// nullable columns are checked against the real customers table. Data is committed then removed afterwards.
@QuarkusTest
class CustomerRepositoryTest {

    @Inject
    CustomerRepository repository;

    @Inject
    EntityManager em;

    private Long customerId;

    @AfterEach
    void tearDown() {
        if (customerId != null) {
            QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("delete from customers where id = ?1")
                    .setParameter(1, customerId).executeUpdate());
        }
    }

    private Long persist(String telephone, String email, String address) {
        return QuarkusTransaction.requiringNew().call(() -> {
            Customer c = new Customer();
            c.setFirstName("Ada");
            c.setLastName("Lovelace");
            c.setTelephone(telephone);
            c.setEmail(email);
            c.setAddress(address);
            repository.persist(c);
            return c.getId();
        });
    }

    @Test
    void _01_ShouldRoundTripCustomer_WhenPersistedAndFound() {
        customerId = persist("123", "ada@example.com", "London");
        Customer found = QuarkusTransaction.requiringNew().call(() -> repository.findById(customerId));
        assertNotNull(found);
        assertEquals("Ada", found.getFirstName());
        assertEquals("Lovelace", found.getLastName());
        assertEquals("123", found.getTelephone());
        assertEquals("ada@example.com", found.getEmail());
        assertEquals("London", found.getAddress());
    }

    @Test
    void _02_ShouldRoundTripNulls_WhenOptionalColumnsAreNull() {
        customerId = persist(null, null, null);
        Customer found = QuarkusTransaction.requiringNew().call(() -> repository.findById(customerId));
        assertNotNull(found);
        assertNull(found.getTelephone());
        assertNull(found.getEmail());
        assertNull(found.getAddress());
    }

    @Test
    void _03_ShouldRemoveRow_WhenCustomerDeleted() {
        customerId = persist(null, null, null);
        QuarkusTransaction.requiringNew().run(() -> repository.deleteById(customerId));
        assertNull(QuarkusTransaction.requiringNew().call(() -> repository.findById(customerId)));
    }
}
