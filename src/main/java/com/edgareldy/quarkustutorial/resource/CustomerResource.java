package com.edgareldy.quarkustutorial.resource;

import com.edgareldy.quarkustutorial.dto.common.ApiResponse;
import com.edgareldy.quarkustutorial.dto.common.PageResponse;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerRequest;
import com.edgareldy.quarkustutorial.dto.ecommerce.CustomerResponse;
import com.edgareldy.quarkustutorial.service.CustomerService;
import io.quarkus.security.PermissionsAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.jboss.resteasy.reactive.ResponseStatus;

/**
 * CRUD endpoints for customers.
 * <p>
 * Created edgar.muhamyangabo on 9/19/26
 * Author : edgar.muhamyangabo
 * Date : 9/19/26
 * Project : quarkus-tutorial
 */
@Path("/api/v1/customers")
@Produces(MediaType.APPLICATION_JSON)
@SecurityRequirement(name = "jwt")
public class CustomerResource {

    @Inject
    CustomerService customerService;

    @GET
    @PermissionsAllowed("CUSTOMER:READ")
    public ApiResponse<PageResponse<CustomerResponse>> list(
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("20") @Min(1) @Max(100) int size) {
        return ApiResponse.success(customerService.list(page, size), "Customers");
    }

    @GET
    @Path("/{id}")
    @PermissionsAllowed("CUSTOMER:READ")
    public ApiResponse<CustomerResponse> get(@PathParam("id") Long id) {
        return ApiResponse.success(customerService.findById(id), "Customer");
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("CUSTOMER:WRITE")
    @ResponseStatus(201)
    public ApiResponse<CustomerResponse> create(@Valid CustomerRequest request) {
        return ApiResponse.success(customerService.create(request), "Customer created");
    }

    @PUT
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @PermissionsAllowed("CUSTOMER:WRITE")
    public ApiResponse<CustomerResponse> update(@PathParam("id") Long id, @Valid CustomerRequest request) {
        return ApiResponse.success(customerService.update(id, request), "Customer updated");
    }

    @DELETE
    @Path("/{id}")
    @PermissionsAllowed("CUSTOMER:WRITE")
    public ApiResponse<Void> delete(@PathParam("id") Long id) {
        customerService.delete(id);
        return ApiResponse.success(null, "Customer deleted");
    }
}
