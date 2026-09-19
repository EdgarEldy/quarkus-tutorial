package com.edgareldy.quarkustutorial.service;

import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerResponse;

/**
 * Contract for customer management.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
public interface CustomerService {

    /** @return one page of customers ordered by id */
    PageResponse<CustomerResponse> list(int page, int size);

    /** @return the customer, or throws ResourceNotFoundException (404) */
    CustomerResponse findById(Long id);

    /** @return the created customer */
    CustomerResponse create(CustomerRequest request);

    /** @return the updated customer, or throws ResourceNotFoundException (404) */
    CustomerResponse update(Long id, CustomerRequest request);

    /** Deletes a customer; throws ResourceNotFoundException (404) if missing. */
    void delete(Long id);
}
