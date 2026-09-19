package com.edgareldy.quarkustutorial.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerResponse;
import com.edgareldy.quarkustutorial.entity.Customer;
import com.edgareldy.quarkustutorial.exception.BusinessRuleException;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CustomerRepository;
import com.edgareldy.quarkustutorial.repository.OrderRepository;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Isolated unit tests of CustomerServiceImpl with the repository mocked.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
// Plain Mockito with the package-private injected field assigned directly (same approach as
// CategoryServiceImplTest): no container is needed to test the mapping and not-found rules.
class CustomerServiceImplTest {

    private CustomerRepository repository;
    private OrderRepository orderRepository;
    private CustomerServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerRepository.class);
        service = new CustomerServiceImpl();
        orderRepository = mock(OrderRepository.class);
        service.customerRepository = repository;
        service.orderRepository = orderRepository;
    }

    private static Customer customer(Long id, String first) {
        Customer c = new Customer();
        c.setId(id);
        c.setFirstName(first);
        c.setLastName("Last");
        c.setTelephone("1");
        c.setEmail("a@b.co");
        c.setAddress("Addr");
        return c;
    }

    @Test
    void findByIdMapsEntity() {
        when(repository.findById(1L)).thenReturn(customer(1L, "Ada"));

        CustomerResponse r = service.findById(1L);

        assertEquals(new CustomerResponse(1L, "Ada", "Last", "1", "a@b.co", "Addr"), r);
    }

    @Test
    void findByIdMissingIsNotFound() {
        when(repository.findById(9L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.findById(9L));
    }

    @Test
    void createPersistsMappedEntityIncludingNullOptionals() {
        CustomerResponse r = service.create(new CustomerRequest("Ada", "Lovelace", null, null, null));

        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(repository).persist(saved.capture());
        assertEquals("Ada", saved.getValue().getFirstName());
        assertEquals("Lovelace", saved.getValue().getLastName());
        assertNull(saved.getValue().getTelephone());
        assertNull(saved.getValue().getEmail());
        assertNull(saved.getValue().getAddress());
        assertEquals("Ada", r.firstName());
        assertNull(r.email());
    }

    @Test
    void updateOverwritesAllFields() {
        Customer existing = customer(1L, "Old");
        when(repository.findById(1L)).thenReturn(existing);

        CustomerResponse r = service.update(1L, new CustomerRequest("New", "Name", null, "n@e.io", "Here"));

        assertEquals("New", existing.getFirstName());
        assertEquals("Name", existing.getLastName());
        assertNull(existing.getTelephone());
        assertEquals("n@e.io", existing.getEmail());
        assertEquals("Here", r.address());
        assertEquals(1L, r.id());
    }

    @Test
    void updateMissingIsNotFound() {
        when(repository.findById(9L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.update(9L, new CustomerRequest("a", "b", null, null, null)));
    }

    @Test
    void deleteRemovesCustomer() {
        Customer c = customer(1L, "Ada");
        when(repository.findById(1L)).thenReturn(c);

        service.delete(1L);

        verify(repository).delete(c);
    }

    @Test
    void deleteIsRefusedWhileOrdersReferenceTheCustomer() {
        Customer c = customer(1L, "Ada");
        when(repository.findById(1L)).thenReturn(c);
        when(orderRepository.countByCustomerId(1L)).thenReturn(2L);

        assertThrows(BusinessRuleException.class, () -> service.delete(1L));
        verify(repository, never()).delete(any(Customer.class));
    }

    @Test
    void deleteMissingIsNotFound() {
        when(repository.findById(9L)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> service.delete(9L));
        verify(repository, never()).delete(any(Customer.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listBuildsPageResponse() {
        PanacheQuery<Customer> all = mock(PanacheQuery.class);
        PanacheQuery<Customer> paged = mock(PanacheQuery.class);
        when(repository.findAll(any(Sort.class))).thenReturn(all);
        when(all.page(any(Page.class))).thenReturn(paged);
        when(paged.list()).thenReturn(List.of(customer(1L, "A"), customer(2L, "B")));
        when(repository.count()).thenReturn(5L);

        PageResponse<CustomerResponse> page = service.list(1, 2);

        assertEquals(2, page.content().size());
        assertEquals("A", page.content().get(0).firstName());
        assertEquals(1, page.page());
        assertEquals(2, page.size());
        assertEquals(5L, page.totalElements());
        assertEquals(3, page.totalPages());
        ArgumentCaptor<Page> requested = ArgumentCaptor.forClass(Page.class);
        verify(all).page(requested.capture());
        assertEquals(1, requested.getValue().index);
        assertEquals(2, requested.getValue().size);
    }
}
