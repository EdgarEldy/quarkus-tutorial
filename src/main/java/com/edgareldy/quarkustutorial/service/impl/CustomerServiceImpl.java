package com.edgareldy.quarkustutorial.service.impl;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerResponse;
import com.edgareldy.quarkustutorial.entity.Customer;
import com.edgareldy.quarkustutorial.exception.ResourceNotFoundException;
import com.edgareldy.quarkustutorial.repository.CustomerRepository;
import com.edgareldy.quarkustutorial.service.CustomerService;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Default {@link CustomerService} backed by Panache.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@ApplicationScoped
public class CustomerServiceImpl implements CustomerService {

    @Inject
    CustomerRepository customerRepository;

    @Override
    @Transactional
    public PageResponse<CustomerResponse> list(int page, int size) {
        List<CustomerResponse> content = customerRepository.findAll(Sort.by("id")).page(Page.of(page, size))
                .list().stream().map(CustomerServiceImpl::toResponse).toList();
        return PageResponse.of(content, page, size, customerRepository.count());
    }

    @Override
    @Transactional
    public CustomerResponse findById(Long id) {
        return toResponse(find(id));
    }

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        customerRepository.persist(customer);
        return toResponse(customer);
    }

    @Override
    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        apply(customer, request);
        return toResponse(customer);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        customerRepository.delete(find(id));
    }

    private Customer find(Long id) {
        Customer customer = customerRepository.findById(id);
        if (customer == null) {
            throw new ResourceNotFoundException("Customer " + id + " not found");
        }
        return customer;
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setTelephone(request.telephone());
        customer.setEmail(request.email());
        customer.setAddress(request.address());
    }

    private static CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getFirstName(), customer.getLastName(),
                customer.getTelephone(), customer.getEmail(), customer.getAddress());
    }
}
